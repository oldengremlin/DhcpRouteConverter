/*
 * Copyright 2025 Ukrcom
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ukrcom.dhcprouteconverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DhcpOptionConverter {

    private static final String IP_REGEX
            = "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final Pattern IP_PATTERN = Pattern.compile("^" + IP_REGEX + "$");
    private static final Pattern LOOPBACK_PATTERN = Pattern.compile("^127\\..*");

    private final GlobalConfig globalConfig;
    private final List<RouterConfig> routers;
    private boolean hasDefaultRoute;
    private final boolean withOption249;

    public enum Format {
        DEFAULT,
        ISC,
        ROUTEROS,
        JUNOS,
        CISCO,
        WINDOWS
    }

    public DhcpOptionConverter(GlobalConfig globalConfig, List<RouterConfig> routers, boolean withOption249) {
        this.globalConfig = globalConfig;
        this.routers = routers;
        this.hasDefaultRoute = false;
        this.withOption249 = withOption249;
    }

    public List<String> generateDhcpOptions() {
        List<String> results = new ArrayList<>();
        hasDefaultRoute = false;

        for (RouterConfig router : routers) {
            for (Map.Entry<String, PoolConfig> entry : router.getPools().entrySet()) {
                String poolName = entry.getKey();
                PoolConfig pool = entry.getValue();

                // Skip pool only if no default-gateway and both disable-append-routes are true
                if (pool.getDefaultGateway() == null && router.isDisableAppendRoutes() && pool.isDisableAppendRoutes()) {
                    System.err.println("DEBUG: Skipping pool " + poolName + " in router " + router.getName() + " due to disable-append-routes and no default-gateway");
                    continue;
                }

                List<String> networks = new ArrayList<>();
                List<String> gateways = new ArrayList<>();
                Map<String, String> routePriorityMap = new LinkedHashMap<>();

                // Priority 3: Default gateway (highest priority)
                if (pool.getDefaultGateway() != null) {
                    if (!IP_PATTERN.matcher(pool.getDefaultGateway()).matches()
                            || pool.getDefaultGateway().equals("0.0.0.0")
                            || pool.getDefaultGateway().equals("255.255.255.255")) {
                        System.err.printf("ERROR: Invalid gateway IP: %s for pool %s%n", pool.getDefaultGateway(), poolName);
                        continue;
                    }
                    routePriorityMap.put("0.0.0.0/0", pool.getDefaultGateway());
                    hasDefaultRoute = true;
                }

                // Priority 2: Common routes from pool
                if (!pool.isDisableAppendRoutes()) {
                    for (Map<String, String> route : pool.getCommonRoutes()) {
                        String network = route.get("network");
                        String gateway = route.get("gateway");
                        if (network == null || gateway == null) {
                            System.err.printf("ERROR: Invalid common route for pool %s%n", poolName);
                            continue;
                        }
                        if (network.equals("0.0.0.0/0")) {
                            System.err.printf("ERROR: Default route (0.0.0.0/0) not allowed in common-routes for pool %s%n", poolName);
                            continue;
                        }
                        if (!IP_PATTERN.matcher(gateway).matches()
                                || gateway.equals("0.0.0.0")
                                || gateway.equals("255.255.255.255")) {
                            System.err.printf("ERROR: Invalid gateway IP: %s for common route in pool %s%n", gateway, poolName);
                            continue;
                        }
                        routePriorityMap.putIfAbsent(network, gateway);
                    }
                }

                // Priority 1: Append routes from global config (lowest priority)
                if (!pool.isDisableAppendRoutes() && !router.isDisableAppendRoutes()) {
                    for (Map<String, String> route : globalConfig.getAppendRoutes()) {
                        String network = route.get("network");
                        String gateway = route.get("gateway");
                        if (network == null || gateway == null) {
                            System.err.printf("ERROR: Invalid append route for pool %s%n", poolName);
                            continue;
                        }
                        if (network.equals("0.0.0.0/0")) {
                            System.err.printf("ERROR: Default route (0.0.0.0/0) not allowed in append-routes for pool %s%n", poolName);
                            continue;
                        }
                        if (!IP_PATTERN.matcher(gateway).matches()
                                || gateway.equals("0.0.0.0")
                                || gateway.equals("255.255.255.255")) {
                            System.err.printf("ERROR: Invalid gateway IP: %s for append route in pool %s%n", gateway, poolName);
                            continue;
                        }
                        routePriorityMap.putIfAbsent(network, gateway);
                    }
                }

                // Convert map to lists for processing, preserving priority order
                for (Map.Entry<String, String> route : routePriorityMap.entrySet()) {
                    networks.add(route.getKey());
                    gateways.add(route.getValue());
                }

                results.addAll(generateDhcpOptionsForPool(networks, gateways, poolName));
            }
        }

        return results;
    }

    private List<String> generateDhcpOptionsForPool(List<String> networks, List<String> gateways, String poolName) {
        List<String> results = new ArrayList<>();
        StringBuilder aggregateHex = new StringBuilder();

        for (int i = 0; i < networks.size() && i < gateways.size(); i++) {
            String net = networks.get(i);
            String gw = gateways.get(i);

            String[] networkParts = net.split("/");
            if (networkParts.length != 2) {
                System.err.printf("Invalid network format: %s for pool %s%n", net, poolName);
                continue;
            }

            String network = networkParts[0];
            int subnetMask;
            try {
                subnetMask = Integer.parseInt(networkParts[1]);
            } catch (NumberFormatException e) {
                System.err.printf("Invalid subnet mask: %s for pool %s%n", networkParts[1], poolName);
                continue;
            }

            if (subnetMask < 0 || subnetMask > 32
                    || !IP_PATTERN.matcher(network).matches()
                    || !IP_PATTERN.matcher(gw).matches()
                    || gw.equals("0.0.0.0")
                    || gw.equals("255.255.255.255")) {
                System.err.printf("Mask %d, network %s or gateway %s error for pool %s%n",
                        subnetMask, network, gw, poolName);
                continue;
            }

            String[] destinationParts = network.split("\\.");
            String[] routerParts = gw.split("\\.");

            int significantOctets = 0;
            if (subnetMask >= 1 && subnetMask <= 8) {
                significantOctets = 1;
            } else if (subnetMask >= 9 && subnetMask <= 16) {
                significantOctets = 2;
            } else if (subnetMask >= 17 && subnetMask <= 24) {
                significantOctets = 3;
            } else if (subnetMask >= 25 && subnetMask <= 32) {
                significantOctets = 4;
            }

            String networkLen = toHex(subnetMask);
            StringBuilder destination = new StringBuilder();
            for (int j = 0; j < significantOctets; j++) {
                destination.append(toHex(Integer.parseInt(destinationParts[j])));
            }

            StringBuilder router = new StringBuilder();
            for (String r : routerParts) {
                router.append(toHex(Integer.parseInt(r)));
            }

            String routeHex = networkLen + destination + router;
            aggregateHex.append(routeHex);
        }

        if (aggregateHex.length() > 0) {
            results.add(String.format("set access address-assignment pool %s family inet dhcp-attributes option 121 hex-string %s", poolName, aggregateHex));
            if (withOption249) {
                results.add(String.format("set access address-assignment pool %s family inet dhcp-attributes option 249 hex-string %s", poolName, aggregateHex));
            }
        }

        return results;
    }

    public List<String> generateDhcpOptions(List<String> networks, List<String> gateways, boolean debug, boolean withWarningLoopback, Format format, String junosPoolName, String ciscoPoolName) {
        List<String> results = new ArrayList<>();
        StringBuilder aggregateHex = new StringBuilder();
        List<Integer> maskList = new ArrayList<>();
        List<String> destinationList = new ArrayList<>();
        List<String> routerList = new ArrayList<>();

        for (int i = 0; i < networks.size() && i < gateways.size(); i++) {
            String net = networks.get(i);
            String gw = gateways.get(i);

            if (withWarningLoopback && LOOPBACK_PATTERN.matcher(gw).matches()) {
                System.err.printf("WARNING: Gateway %s is in loopback range (127.0.0.0/8)%n", gw);
            }

            String[] networkParts = net.split("/");
            if (networkParts.length != 2) {
                System.err.printf("Invalid network format: %s%n", net);
                continue;
            }

            String network = networkParts[0];
            int subnetMask;
            try {
                subnetMask = Integer.parseInt(networkParts[1]);
            } catch (NumberFormatException e) {
                System.err.printf("Invalid subnet mask: %s%n", networkParts[1]);
                continue;
            }

            if (subnetMask < 0 || subnetMask > 32
                    || !IP_PATTERN.matcher(network).matches()
                    || !IP_PATTERN.matcher(gw).matches()
                    || gw.equals("0.0.0.0")
                    || gw.equals("255.255.255.255")) {
                System.err.printf("Mask %d, network %s or gateway %s error%n",
                        subnetMask, network, gw);
                continue;
            }

            String[] destinationParts = network.split("\\.");
            String[] routerParts = gw.split("\\.");

            int significantOctets = 0;
            if (subnetMask >= 1 && subnetMask <= 8) {
                significantOctets = 1;
            } else if (subnetMask >= 9 && subnetMask <= 16) {
                significantOctets = 2;
            } else if (subnetMask >= 17 && subnetMask <= 24) {
                significantOctets = 3;
            } else if (subnetMask >= 25 && subnetMask <= 32) {
                significantOctets = 4;
            }

            String networkLen = toHex(subnetMask);
            StringBuilder destination = new StringBuilder();
            for (int j = 0; j < significantOctets; j++) {
                destination.append(toHex(Integer.parseInt(destinationParts[j])));
            }

            StringBuilder router = new StringBuilder();
            for (String r : routerParts) {
                router.append(toHex(Integer.parseInt(r)));
            }

            String routeHex = networkLen + destination + router;
            aggregateHex.append(routeHex);

            if (debug) {
                System.out.println(String.format("option_121_route_%s_via_%s : 0x%s", net, gw, routeHex));
                if (withOption249) {
                    System.out.println(String.format("option_249_route_%s_via_%s : 0x%s", net, gw, routeHex));
                }
            }

            maskList.add(subnetMask);
            destinationList.add(network);
            routerList.add(gw);
            if (net.equals("0.0.0.0/0")) {
                hasDefaultRoute = true;
            }
        }

        if (aggregateHex.length() == 0) {
            return results;
        }

        switch (format) {
            case DEFAULT -> {
                results.add(String.format("aggregate_opt_121 : 0x%s", aggregateHex));
                if (withOption249) {
                    results.add(String.format("aggregate_opt_249 : 0x%s", aggregateHex));
                }
            }
            case ISC -> {
                results.add("option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;");
                if (withOption249) {
                    results.add("option ms-classless-static-routes code 249 = array of unsigned integer 8;");
                }
                StringBuilder opt121 = new StringBuilder("option rfc3442-classless-static-routes ");
                StringBuilder opt249 = new StringBuilder("option ms-classless-static-routes ");
                for (int i = 0; i < maskList.size(); i++) {
                    String[] destParts = destinationList.get(i).split("\\.");
                    String[] gwParts = routerList.get(i).split("\\.");
                    int significantOctets = maskList.get(i) <= 8 ? 1 : maskList.get(i) <= 16 ? 2 : maskList.get(i) <= 24 ? 3 : 4;

                    if (i > 0) {
                        opt121.append(", ");
                        if (withOption249) {
                            opt249.append(", ");
                        }
                    }
                    opt121.append(maskList.get(i));
                    if (withOption249) {
                        opt249.append(maskList.get(i));
                    }
                    for (int j = 0; j < significantOctets; j++) {
                        opt121.append(",").append(destParts[j]);
                        if (withOption249) {
                            opt249.append(",").append(destParts[j]);
                        }
                    }
                    for (String gwPart : gwParts) {
                        opt121.append(",").append(gwPart);
                        if (withOption249) {
                            opt249.append(",").append(gwPart);
                        }
                    }
                }
                opt121.append(";");
                results.add(opt121.toString());
                if (withOption249) {
                    opt249.append(";");
                    results.add(opt249.toString());
                }
            }
            case ROUTEROS -> {
                results.add(String.format("/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x%s", aggregateHex));
                if (withOption249) {
                    results.add(String.format("/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x%s", aggregateHex));
                }
            }
            case JUNOS -> {
                results.add(String.format("set access address-assignment pool %s family inet dhcp-attributes option 121 hex-string %s", junosPoolName, aggregateHex));
                if (withOption249) {
                    results.add(String.format("set access address-assignment pool %s family inet dhcp-attributes option 249 hex-string %s", junosPoolName, aggregateHex));
                }
            }
            case CISCO -> {
                results.add(String.format("ip dhcp pool %s", ciscoPoolName));
                results.add(String.format(" option 121 hex %s", aggregateHex));
                if (withOption249) {
                    results.add(String.format(" option 249 hex %s", aggregateHex));
                }
            }
            case WINDOWS -> {
                results.add(String.format("Set-DhcpServerv4OptionValue -OptionId 121 -Value 0x%s", aggregateHex));
                if (withOption249) {
                    results.add(String.format("Set-DhcpServerv4OptionValue -OptionId 249 -Value 0x%s", aggregateHex));
                }
            }
            default -> {
                throw new AssertionError(format.name());
            }
        }

        return results;
    }

    public List<String> parseDhcpOptions(String option) {
        List<String> results = new ArrayList<>();
        if (option == null) {
            System.err.println("Invalid hex option format");
            return results;
        }

        String hex = option.toLowerCase();
        if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }

        if (!hex.matches("^[0-9a-f]+$")) {
            System.err.println("Invalid hex option format");
            return results;
        }

        int index = 0;
        while (index < hex.length()) {
            if (index + 2 > hex.length()) {
                System.err.println("Incomplete option data");
                break;
            }
            String maskHex = hex.substring(index, index + 2);
            int subnetMask;
            try {
                subnetMask = Integer.parseInt(maskHex, 16);
            } catch (NumberFormatException e) {
                System.err.println("Invalid subnet mask in option");
                break;
            }
            if (subnetMask < 0 || subnetMask > 32) {
                System.err.println("Subnet mask out of range");
                break;
            }
            index += 2;

            int significantOctets = 0;
            if (subnetMask >= 1 && subnetMask <= 8) {
                significantOctets = 1;
            } else if (subnetMask >= 9 && subnetMask <= 16) {
                significantOctets = 2;
            } else if (subnetMask >= 17 && subnetMask <= 24) {
                significantOctets = 3;
            } else if (subnetMask >= 25 && subnetMask <= 32) {
                significantOctets = 4;
            }

            int[] destination = new int[4];
            for (int i = 0; i < significantOctets; i++) {
                if (index + 2 > hex.length()) {
                    System.err.println("Incomplete destination address");
                    return results;
                }
                String octetHex = hex.substring(index, index + 2);
                destination[i] = Integer.parseInt(octetHex, 16);
                index += 2;
            }
            for (int i = significantOctets; i < 4; i++) {
                destination[i] = 0;
            }

            int[] gateway = new int[4];
            for (int i = 0; i < 4; i++) {
                if (index + 2 > hex.length()) {
                    System.err.println("Incomplete gateway address");
                    return results;
                }
                String octetHex = hex.substring(index, index + 2);
                gateway[i] = Integer.parseInt(octetHex, 16);
                index += 2;
            }

            String network = String.format("%d.%d.%d.%d/%d",
                    destination[0], destination[1], destination[2], destination[3], subnetMask);
            String gw = String.format("%d.%d.%d.%d",
                    gateway[0], gateway[1], gateway[2], gateway[3]);
            results.add(String.format("%s via %s", network, gw));
            if (network.equals("0.0.0.0/0")) {
                hasDefaultRoute = true;
            }
        }

        return results;
    }

    public boolean hasDefaultRoute() {
        return hasDefaultRoute;
    }

    private static String toHex(int n) {
        if (n == 0) {
            return "00";
        }
        String hex = Integer.toHexString(n);
        return hex.length() == 1 ? "0" + hex : hex;
    }
}
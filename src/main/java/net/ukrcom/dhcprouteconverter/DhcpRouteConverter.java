/*
 * Copyright 2025 olden
 *
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
import java.util.List;
import java.util.regex.Pattern;

public class DhcpRouteConverter {

    private static final String IP_REGEX
            = "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final Pattern IP_PATTERN = Pattern.compile("^" + IP_REGEX + "$");

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        String option = args[0];
        if (option.equals("--help") || option.equals("-?")) {
            printHelp();
            return;
        }

        boolean debug = false;
        String input = null;
        if (option.equals("--to-dhcp-options") || option.equals("-tdo")) {
            if (args.length < 2) {
                System.err.println("Error: Missing arguments for -tdo. Use --help for usage information.");
                return;
            }
            if (args.length > 2 && args[1].equals("-d")) {
                debug = true;
                input = args[2];
            } else {
                input = args[1];
            }
        } else if (option.equals("--from-dhcp-options") || option.equals("-fdo")) {
            if (args.length < 2) {
                System.err.println("Error: Missing arguments for -fdo. Use --help for usage information.");
                return;
            }
            input = args[1];
        } else {
            System.err.println("Error: Unknown option. Use --help for usage information.");
            return;
        }

        if (option.equals("-tdo") || option.equals("--to-dhcp-options")) {
            String[] pairs = input.split(",");
            if (pairs.length % 2 != 0) {
                System.err.println("Error: Network and gateway pairs must be complete.");
                return;
            }

            List<String> networks = new ArrayList<>();
            List<String> gateways = new ArrayList<>();
            for (int i = 0; i < pairs.length; i += 2) {
                networks.add(pairs[i]);
                gateways.add(pairs[i + 1]);
            }

            List<String> dhcpOptions = generateDhcpOptions(networks, gateways, debug);
            for (String dhcpOption : dhcpOptions) {
                System.out.println(dhcpOption);
            }
        } else if (option.equals("-fdo") || option.equals("--from-dhcp-options")) {
            List<String> routes = parseDhcpOptions(input);
            for (String route : routes) {
                System.out.println(route);
            }
        }
    }

    private static void printHelp() {
        System.out.println("DhcpRouteConverter - A utility to convert between network routes and DHCP options 121/249.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  DhcpRouteConverter [OPTION] [ARGUMENT]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --to-dhcp-options, -tdo [-d] <network1,gateway1,network2,gateway2,...>");
        System.out.println("      Convert a comma-separated list of network/gateway pairs to DHCP options.");
        System.out.println("      Use -d to display individual route options.");
        System.out.println("      Example: 192.168.1.0/24,192.168.0.1,10.0.0.0/8,10.0.0.1");
        System.out.println();
        System.out.println("  --from-dhcp-options, -fdo <hex-option>");
        System.out.println("      Decode a hexadecimal DHCP option string to network/gateway pairs.");
        System.out.println("      Example: 18c0a801c0a80001 or 0x18c0a801c0a80001");
        System.out.println();
        System.out.println("  --help, -?");
        System.out.println("      Display this help message.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  DhcpRouteConverter -tdo 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -tdo -d 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -fdo 18c0a801c0a80001");
    }

    /**
     * Generates DHCP options 121 and 249 from a list of networks and gateways.
     *
     * @param networks List of networks in format "a.b.c.d/m"
     * @param gateways List of gateway IP addresses
     * @param debug If true, print individual route options
     * @return List of formatted DHCP options
     */
    public static List<String> generateDhcpOptions(List<String> networks, List<String> gateways, boolean debug) {
        List<String> results = new ArrayList<>();
        StringBuilder aggregate = new StringBuilder();

        for (int i = 0; i < networks.size() && i < gateways.size(); i++) {
            String net = networks.get(i);
            String gw = gateways.get(i);

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
                    || !IP_PATTERN.matcher(gw).matches()) {
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
            aggregate.append(routeHex);

            if (debug) {
                System.out.println(String.format("option_121_route_%s_via_%s : 0x%s", net, gw, routeHex));
                System.out.println(String.format("option_249_route_%s_via_%s : 0x%s", net, gw, routeHex));
            }
        }

        if (aggregate.length() > 0) {
            results.add(String.format("aggregate_opt_121 : 0x%s", aggregate));
            results.add(String.format("aggregate_opt_249 : 0x%s", aggregate));
        }

        return results;
    }

    /**
     * Parses DHCP options 121 or 249 and extracts networks and gateways.
     *
     * @param option Hex string of DHCP option (e.g., "18c0a801c0a80001" or
     * "0x18c0a801c0a80001")
     * @return List of strings in format "network/mask via gateway"
     */
    public static List<String> parseDhcpOptions(String option) {
        List<String> results = new ArrayList<>();
        if (option == null) {
            System.err.println("Invalid hex option format");
            return results;
        }

        // Remove optional "0x" prefix
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
            // Read subnet mask (1 byte)
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

            // Calculate number of significant octets
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

            // Read destination address
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

            // Read gateway address (always 4 octets)
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

            // Format result
            String network = String.format("%d.%d.%d.%d/%d",
                    destination[0], destination[1], destination[2], destination[3], subnetMask);
            String gw = String.format("%d.%d.%d.%d",
                    gateway[0], gateway[1], gateway[2], gateway[3]);
            results.add(String.format("%s via %s", network, gw));
        }

        return results;
    }

    static String toHex(int n) {
        if (n == 0) {
            return "00";
        }
        String hex = Integer.toHexString(n);
        return hex.length() == 1 ? "0" + hex : hex.substring(hex.length() - 2);
    }
}

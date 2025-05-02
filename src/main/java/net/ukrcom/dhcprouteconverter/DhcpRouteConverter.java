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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DhcpRouteConverter {

    private static final String IP_REGEX
            = "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final Pattern IP_PATTERN = Pattern.compile("^" + IP_REGEX + "$");

    enum Format {
        DEFAULT,
        ISC,
        ROUTEROS,
        JUNOS,
        CISCO,
        WINDOWS
    }

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
        Format format = Format.DEFAULT;
        String junosPoolName = "lan-pool"; // Default pool name for JunOS
        switch (option) {
            case "--to-dhcp-options":
            case "-tdo":
                if (args.length < 2) {
                    System.err.println("Error: Missing arguments for -tdo. Use --help for usage information.");
                    return;
                }   int argIndex = 1;
                if (args.length > argIndex && args[argIndex].equals("-d")) {
                    debug = true;
                    argIndex++;
                }   // Check for format arguments
                List<String> formatArgs = Arrays.asList("--isc", "--routeros", "--junos", "--cisco", "--windows");
                String formatArg = null;
                if (args.length > argIndex && formatArgs.contains(args[argIndex])) {
                    formatArg = args[argIndex];
                    argIndex++;
                } else if (args.length > argIndex && args[argIndex].startsWith("--junos=")) {
                    formatArg = "--junos";
                    junosPoolName = args[argIndex].substring("--junos=".length());
                    if (junosPoolName.isEmpty()) {
                        System.err.println("Error: JunOS pool name cannot be empty.");
                        return;
                    }
                    argIndex++;
                }   // Validate mutual exclusivity of format arguments
                long formatCount = Arrays.stream(args).filter(arg -> formatArgs.contains(arg) || arg.startsWith("--junos=")).count();
                if (formatCount > 1) {
                    System.err.println("Error: Only one format can be specified (--isc, --routeros, --junos, --cisco, --windows).");
                    return;
                }   if (formatArg != null) {
                    switch (formatArg) {
                        case "--isc" ->
                            format = Format.ISC;
                        case "--routeros" ->
                            format = Format.ROUTEROS;
                        case "--junos" ->
                            format = Format.JUNOS;
                        case "--cisco" ->
                            format = Format.CISCO;
                        case "--windows" ->
                            format = Format.WINDOWS;
                    }
                }   if (args.length <= argIndex) {
                    System.err.println("Error: Missing input for -tdo. Use --help for usage information.");
                    return;
                }   input = args[argIndex];
                break;
            case "--from-dhcp-options":
            case "-fdo":
                if (args.length < 2) {
                    System.err.println("Error: Missing arguments for -fdo. Use --help for usage information.");
                    return;
                }   input = args[1];
                break;
            default:
                System.err.println("Error: Unknown option. Use --help for usage information.");
                return;
        }

        // TODO: Add IPv6 support in future versions (e.g., --to-dhcpv6-options)
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

            List<String> dhcpOptions = generateDhcpOptions(networks, gateways, debug, format, junosPoolName);
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
        System.out.println("  --to-dhcp-options, -tdo [-d] [--isc | --routeros | --junos[=pool-name] | --cisco | --windows] <network1,gateway1,network2,gateway2,...>");
        System.out.println("      Convert a comma-separated list of network/gateway pairs to DHCP options.");
        System.out.println("      Use -d to display individual route options.");
        System.out.println("      Use --isc for isc-dhcp-server, --routeros for MikroTik RouterOS, --junos for Juniper JunOS,");
        System.out.println("      --cisco for Cisco IOS, or --windows for Windows DHCP (PowerShell).");
        System.out.println("      For JunOS, optionally specify pool-name (default: lan-pool) with --junos=pool-name.");
        System.out.println("      Default output is aggregate_opt_121/249 hex strings.");
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
        System.out.println("  DhcpRouteConverter -tdo -d --isc 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -tdo --routeros 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -tdo --junos=vlan100-pool 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -tdo --cisco 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -tdo --windows 192.168.1.0/24,192.168.0.1");
        System.out.println("  DhcpRouteConverter -fdo 18c0a801c0a80001");
    }

    /**
     * Generates DHCP options 121 and 249 from a list of networks and gateways.
     *
     * @param networks List of networks in format "a.b.c.d/m"
     * @param gateways List of gateway IP addresses
     * @param debug If true, print individual route options
     * @param format Output format
     * @param junosPoolName Pool name for JunOS format
     * @return List of formatted DHCP options
     */
    public static List<String> generateDhcpOptions(List<String> networks, List<String> gateways, boolean debug, Format format, String junosPoolName) {
        List<String> results = new ArrayList<>();
        StringBuilder aggregateHex = new StringBuilder();

        // Store route data for format-specific output
        List<Integer> maskList = new ArrayList<>();
        List<String> destinationList = new ArrayList<>();
        List<String> routerList = new ArrayList<>();

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
            aggregateHex.append(routeHex);

            if (debug) {
                System.out.println(String.format("option_121_route_%s_via_%s : 0x%s", net, gw, routeHex));
                System.out.println(String.format("option_249_route_%s_via_%s : 0x%s", net, gw, routeHex));
            }

            maskList.add(subnetMask);
            destinationList.add(network);
            routerList.add(gw);
        }

        if (aggregateHex.length() == 0) {
            return results;
        }

        // Generate output based on format
        switch (format) {
            case DEFAULT:
                results.addAll(formatDefault(aggregateHex.toString()));
                break;
            case ISC:
                results.addAll(formatIsc(maskList, destinationList, routerList));
                break;
            case ROUTEROS:
                results.addAll(formatRouterOs(aggregateHex.toString()));
                break;
            case JUNOS:
                results.addAll(formatJunos(aggregateHex.toString(), junosPoolName));
                break;
            case CISCO:
                results.addAll(formatCisco(aggregateHex.toString()));
                break;
            case WINDOWS:
                results.addAll(formatWindows(aggregateHex.toString()));
                break;
        }

        return results;
    }

    /**
     * Formats DHCP options in the default aggregate hex string format.
     *
     * @param aggregateHex The aggregated hex string for all routes
     * @return List of formatted strings (aggregate_opt_121 and
     * aggregate_opt_249)
     */
    private static List<String> formatDefault(String aggregateHex) {
        List<String> results = new ArrayList<>();
        results.add(String.format("aggregate_opt_121 : 0x%s", aggregateHex));
        results.add(String.format("aggregate_opt_249 : 0x%s", aggregateHex));
        return results;
    }

    /**
     * Formats DHCP options for isc-dhcp-server.
     *
     * @param maskList List of subnet masks
     * @param destinationList List of destination network addresses
     * @param routerList List of gateway addresses
     * @return List of formatted strings for isc-dhcp-server configuration
     */
    private static List<String> formatIsc(List<Integer> maskList, List<String> destinationList, List<String> routerList) {
        List<String> results = new ArrayList<>();
        results.add("option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;");
        StringBuilder opt121 = new StringBuilder("option rfc3442-classless-static-routes ");
        results.add("option ms-classless-static-routes code 249 = array of unsigned integer 8;");
        StringBuilder opt249 = new StringBuilder("option ms-classless-static-routes ");

        for (int i = 0; i < maskList.size(); i++) {
            String[] destParts = destinationList.get(i).split("\\.");
            String[] gwParts = routerList.get(i).split("\\.");
            int significantOctets = maskList.get(i) <= 8 ? 1 : maskList.get(i) <= 16 ? 2 : maskList.get(i) <= 24 ? 3 : 4;

            if (i > 0) {
                opt121.append(", ");
                opt249.append(", ");
            }
            opt121.append(maskList.get(i));
            opt249.append(maskList.get(i));
            for (int j = 0; j < significantOctets; j++) {
                opt121.append(",").append(destParts[j]);
                opt249.append(",").append(destParts[j]);
            }
            for (String gwPart : gwParts) {
                opt121.append(",").append(gwPart);
                opt249.append(",").append(gwPart);
            }
        }
        opt121.append(";");
        opt249.append(";");
        results.add(opt121.toString());
        results.add(opt249.toString());

        return results;
    }

    /**
     * Formats DHCP options for MikroTik RouterOS.
     *
     * @param aggregateHex The aggregated hex string for all routes
     * @return List of formatted strings for RouterOS configuration
     */
    private static List<String> formatRouterOs(String aggregateHex) {
        List<String> results = new ArrayList<>();
        results.add(String.format("/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x%s", aggregateHex));
        results.add(String.format("/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x%s", aggregateHex));
        return results;
    }

    /**
     * Formats DHCP options for Juniper JunOS.
     *
     * @param aggregateHex The aggregated hex string for all routes
     * @param poolName The name of the address pool
     * @return List of formatted strings for JunOS configuration
     */
    private static List<String> formatJunos(String aggregateHex, String poolName) {
        List<String> results = new ArrayList<>();
        results.add(String.format("set access address-assignment pool %s family inet dhcp-attributes option 121 hex-string %s", poolName, aggregateHex));
        results.add(String.format("set access address-assignment pool %s family inet dhcp-attributes option 249 hex-string %s", poolName, aggregateHex));
        return results;
    }

    /**
     * Formats DHCP options for Cisco IOS.
     *
     * @param aggregateHex The aggregated hex string for all routes
     * @return List of formatted strings for Cisco IOS configuration
     */
    private static List<String> formatCisco(String aggregateHex) {
        List<String> results = new ArrayList<>();
        results.add("ip dhcp pool mypool");
        results.add(String.format(" option 121 hex %s", aggregateHex));
        results.add(String.format(" option 249 hex %s", aggregateHex));
        return results;
    }

    /**
     * Formats DHCP options for Windows DHCP (PowerShell).
     *
     * @param aggregateHex The aggregated hex string for all routes
     * @return List of formatted strings for Windows DHCP configuration
     */
    private static List<String> formatWindows(String aggregateHex) {
        List<String> results = new ArrayList<>();
        results.add(String.format("Set-DhcpServerv4OptionValue -OptionId 121 -Value 0x%s", aggregateHex));
        results.add(String.format("Set-DhcpServerv4OptionValue -OptionId 249 -Value 0x%s", aggregateHex));
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

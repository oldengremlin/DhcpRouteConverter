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
import java.util.List;

import net.ukrcom.dhcprouteconverter.DhcpOptionConverter.Format;

/**
 * Formats DHCP option strings for various DHCP server configurations.
 */
public class OutputFormatter {

    /**
     * Formats DHCP options for the specified format.
     *
     * @param aggregateHex Hexadecimal string of DHCP options.
     * @param withOption249 If true, includes option 249 (Microsoft-specific).
     * @param format Output format (e.g., ISC, JUNOS, CISCO).
     * @param junosPoolName Pool name for JunOS format.
     * @param ciscoPoolName Pool name for Cisco format.
     * @return List of formatted DHCP option strings.
     */
    public List<String> formatDhcpOptions(String aggregateHex, boolean withOption249, Format format, String junosPoolName,
            String ciscoPoolName) {
        List<String> results = new ArrayList<>();
        if (aggregateHex.isEmpty()) {
            return results;
        }

        switch (format) {
            case DEFAULT:
                results.add(String.format("aggregate_opt_121 : 0x%s", aggregateHex));
                if (withOption249) {
                    results.add(String.format("aggregate_opt_249 : 0x%s", aggregateHex));
                }
                break;
            case ISC:
                results.add("option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;");
                if (withOption249) {
                    results.add("option ms-classless-static-routes code 249 = array of unsigned integer 8;");
                }
                StringBuilder opt121 = new StringBuilder("option rfc3442-classless-static-routes ");
                StringBuilder opt249 = withOption249 ? new StringBuilder("option ms-classless-static-routes ") : null;
                List<String> routes = parseHexToRoutes(aggregateHex);
                for (int i = 0; i < routes.size(); i++) {
                    // Convert "network/mask via gateway" to ISC format
                    String[] parts = routes.get(i).split(" via ");
                    String[] netParts = parts[0].split("/");
                    int mask = Integer.parseInt(netParts[1]);
                    String[] destOctets = netParts[0].split("\\.");
                    StringBuilder iscRoute = new StringBuilder();
                    iscRoute.append(mask);
                    int significantOctets = mask == 0 ? 0 : (mask + 7) / 8;
                    for (int j = 0; j < significantOctets; j++) {
                        iscRoute.append(",").append(destOctets[j]);
                    }
                    String[] gwOctets = parts[1].split("\\.");
                    for (String gwOctet : gwOctets) {
                        iscRoute.append(",").append(gwOctet);
                    }
                    opt121.append(iscRoute);
                    if (withOption249) {
                        opt249.append(iscRoute);
                    }
                    if (i < routes.size() - 1) {
                        opt121.append(",");
                        if (withOption249) {
                            opt249.append(",");
                        }
                    }
                }
                results.add(opt121.toString());
                if (withOption249) {
                    results.add(opt249.toString());
                }
                break;
            case ROUTEROS:
                results.add(String.format("/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x%s",
                        aggregateHex));
                if (withOption249) {
                    results.add(String.format("/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x%s",
                            aggregateHex));
                }
                break;
            case JUNOS:
                results.add(String.format(
                        "set access address-assignment pool %s family inet dhcp-attributes option 121 hex-string %s",
                        junosPoolName, aggregateHex));
                if (withOption249) {
                    results.add(String.format(
                            "set access address-assignment pool %s family inet dhcp-attributes option 249 hex-string %s",
                            junosPoolName, aggregateHex));
                }
                break;
            case CISCO:
                results.add(String.format("ip dhcp pool %s", ciscoPoolName));
                results.add(String.format(" option 121 hex %s", aggregateHex));
                if (withOption249) {
                    results.add(String.format(" option 249 hex %s", aggregateHex));
                }
                break;
            case WINDOWS:
                results.add(String.format("Set-DhcpServerv4OptionValue -OptionId 121 -Value 0x%s", aggregateHex));
                if (withOption249) {
                    results.add(String.format("Set-DhcpServerv4OptionValue -OptionId 249 -Value 0x%s", aggregateHex));
                }
                break;
            default:
                throw new AssertionError("Unknown format: " + format);
        }

        return results;
    }

    /**
     * Parses hexadecimal DHCP option string into route strings.
     *
     * @param aggregateHex Hexadecimal string of DHCP options.
     * @return List of route strings (e.g., "192.168.1.0/24 via 10.0.0.1").
     */
    List<String> parseHexToRoutes(String aggregateHex) {
        List<String> routes = new ArrayList<>();
        int index = 0;

        while (index < aggregateHex.length()) {
            // Read subnet mask (1 byte)
            if (index + 2 > aggregateHex.length()) {
                System.err.println("ERROR: Incomplete hex string at subnet mask");
                break;
            }
            String maskHex = aggregateHex.substring(index, index + 2);
            int subnetMask;
            try {
                subnetMask = Integer.parseInt(maskHex, 16);
            } catch (NumberFormatException e) {
                System.err.printf("ERROR: Invalid subnet mask in hex: %s%n", maskHex);
                break;
            }
            if (subnetMask < 0 || subnetMask > 32) {
                System.err.printf("ERROR: Invalid subnet mask in hex: %s%n", maskHex);
                break;
            }
            index += 2;

            // Calculate number of significant octets
            int significantOctets = subnetMask == 0 ? 0 : (subnetMask + 7) / 8;

            // Read destination network
            int[] destination = new int[4];
            for (int i = 0; i < significantOctets; i++) {
                if (index + 2 > aggregateHex.length()) {
                    System.err.println("ERROR: Incomplete hex string at destination");
                    return routes;
                }
                String octet = aggregateHex.substring(index, index + 2);
                destination[i] = Integer.parseInt(octet, 16);
                index += 2;
            }

            // Read gateway (4 bytes)
            int[] gateway = new int[4];
            for (int i = 0; i < 4; i++) {
                if (index + 2 > aggregateHex.length()) {
                    System.err.println("ERROR: Incomplete hex string at gateway");
                    return routes;
                }
                String octet = aggregateHex.substring(index, index + 2);
                gateway[i] = Integer.parseInt(octet, 16);
                index += 2;
            }

            // Format route as "network/mask via gateway"
            String network = String.format("%d.%d.%d.%d/%d",
                    destination[0], destination[1], destination[2], destination[3], subnetMask);
            String gw = String.format("%d.%d.%d.%d",
                    gateway[0], gateway[1], gateway[2], gateway[3]);
            routes.add(String.format("%s via %s", network, gw));
        }

        return routes;
    }

    /**
     * Formats a list of DHCP options into a single string.
     *
     * @param dhcpOptions List of DHCP option strings.
     * @return Formatted string with options separated by newlines.
     */
    public String format(List<String> dhcpOptions) {
        StringBuilder output = new StringBuilder();
        for (String option : dhcpOptions) {
            output.append(option).append("\n");
        }
        return output.toString();
    }

    /**
     * Applies the configuration to a device (not implemented).
     *
     * @param config Configuration string.
     * @param username Username for authentication.
     * @param password Password for authentication.
     * @param method Application method.
     */
    public void apply(String config, String username, String password, ApplyMethod method) {
        System.err.println("DEBUG: Applying config via " + method + " (username: " + username + ") - not implemented yet");
    }
}

/*
 * Copyright 2025 ukr-com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ukrcom.dhcprouteconverter.outputFormat;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author olden
 */
public class ISC extends outputFormatAbstract implements outputFormat {

    public ISC(String aggregateHex, boolean withOption249, String poolName) {
        super(aggregateHex, withOption249, poolName);
    }

    /**
     * Formats DHCP options for the specified format.
     *
     * @return List of formatted DHCP option strings.
     */
    @Override
    public List<String> formatDhcpOptions() {
        List<String> results = new ArrayList<>();
        if (aggregateHex.isEmpty()) {
            return results;
        }

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

        return results;
    }

    /**
     * Parses hexadecimal DHCP option string into route strings.
     *
     * @param aggregateHex Hexadecimal string of DHCP options.
     * @return List of route strings (e.g., "192.168.1.0/24 via 10.0.0.1").
     */
    private List<String> parseHexToRoutes(String aggregateHex) {
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

}

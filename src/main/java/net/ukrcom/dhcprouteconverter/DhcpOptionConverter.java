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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts network routes to DHCP option 121 or 249 hex strings.
 */
public class DhcpOptionConverter {

    private boolean hasDefaultRoute;
    private boolean debugMode;

    public enum Format {
        DEFAULT, ISC, ROUTEROS, JUNOS, CISCO, WINDOWS
    }

    public DhcpOptionConverter() {
        this.debugMode = false;
        this.hasDefaultRoute = false;
    }

    /**
     * Generates DHCP options from lists of networks and gateways.
     *
     * @param networks List of networks (e.g., "192.168.1.0/24", "0.0.0.0/0").
     * @param gateways List of gateways (e.g., "10.0.0.1").
     * @param debugMode If true, outputs debug information.
     * @param withOption249 If true, includes option 249 (Microsoft-specific).
     * @param format Output format (e.g., ISC, JUNOS).
     * @param junosPoolName Pool name for JunOS format.
     * @param ciscoPoolName Pool name for Cisco format.
     * @return List of formatted DHCP option strings.
     */
    public List<String> generateDhcpOptions(List<String> networks, List<String> gateways, boolean debugMode,
            boolean withOption249, Format format, String junosPoolName, String ciscoPoolName) {

        this.debugMode = debugMode;

        if (networks.size() != gateways.size()) {
            System.err.println("ERROR: Mismatch between networks and gateways count");
            return new ArrayList<>();
        }

        String aggregateHex = "";
        for (int i = 0; i < networks.size(); i++) {
            String hex = convertToHexRoute(networks.get(i), gateways.get(i));
            if (!hex.isEmpty()) {
                aggregateHex += hex;
            }
        }

        if (aggregateHex.isEmpty()) {
            return new ArrayList<>();
        }

        if (this.debugMode) {
            System.out.println("DEBUG: Generated hex string: " + aggregateHex);
        }

        OutputFormatter formatter = new OutputFormatter();
        return formatter.formatDhcpOptions(aggregateHex, withOption249, format, junosPoolName, ciscoPoolName);
    }

    /**
     * Converts a network and gateway to a DHCP option hex string.
     *
     * @param network Network in CIDR format (e.g., "192.168.1.0/24").
     * @param gateway Gateway IP (e.g., "10.0.0.1").
     * @return Hex string for the route, or empty if invalid.
     */
    String convertToHexRoute(String network, String gateway) {
        // Parse network and subnet mask
        Pattern cidrPattern = Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");
        Matcher matcher = cidrPattern.matcher(network);
        if (!matcher.matches()) {
            System.err.println("ERROR: Invalid network format: " + network);
            return "";
        }

        String[] networkParts = matcher.group(1).split("\\.");
        int subnetMask;
        try {
            subnetMask = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            System.err.println("ERROR: Invalid subnet mask: " + matcher.group(2));
            return "";
        }

        if (subnetMask < 0 || subnetMask > 32) {
            System.err.println("ERROR: Invalid subnet mask: " + subnetMask);
            return "";
        }

        if (subnetMask == 0) {
            hasDefaultRoute = true;
        }

        // Parse gateway
        Pattern ipPattern = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
        matcher = ipPattern.matcher(gateway);
        if (!matcher.matches()) {
            System.err.println("ERROR: Invalid gateway format: " + gateway);
            return "";
        }

        int[] gatewayOctets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                gatewayOctets[i] = Integer.parseInt(matcher.group(i + 1));
                if (gatewayOctets[i] < 0 || gatewayOctets[i] > 255) {
                    System.err.println("ERROR: Invalid gateway octet: " + matcher.group(i + 1));
                    return "";
                }
            } catch (NumberFormatException e) {
                System.err.println("ERROR: Invalid gateway octet: " + matcher.group(i + 1));
                return "";
            }
        }

        if (this.debugMode) {
            // Debug: Log network and gateway parsing
            System.out.println("DEBUG: Parsing network: " + network + ", gateway: " + gateway);
            System.out.println("DEBUG: Gateway octets: " + Arrays.toString(gatewayOctets));
        }

        // Convert to hex
        StringBuilder hex = new StringBuilder();
        hex.append(String.format("%02x", subnetMask)); // Subnet mask

        // Network octets (only significant ones based on mask)
        int significantOctets = subnetMask == 0 ? 0 : (subnetMask + 7) / 8;
        for (int i = 0; i < significantOctets; i++) {
            try {
                int octet = Integer.parseInt(networkParts[i]);
                if (octet < 0 || octet > 255) {
                    System.err.println("ERROR: Invalid network octet: " + networkParts[i]);
                    return "";
                }
                hex.append(String.format("%02x", octet));
            } catch (NumberFormatException e) {
                System.err.println("ERROR: Invalid network octet: " + networkParts[i]);
                return "";
            }
        }

        // Gateway octets
        for (int octet : gatewayOctets) {
            hex.append(String.format("%02x", octet));
        }

        if (this.debugMode) {
            // Debug: Log generated hex
            System.out.println("DEBUG: Generated hex for route: " + hex);
        }

        return hex.toString();
    }

    /**
     * Parses a DHCP option hex string into route strings.
     *
     * @param hexString Hexadecimal string of DHCP options.
     * @return List of route strings (e.g., "192.168.1.0/24 via 10.0.0.1").
     */
    public List<String> parseDhcpOptions(String hexString) {
        List<String> routes = new ArrayList<>();
        int index = 0;

        while (index < hexString.length()) {
            // Read subnet mask (1 byte)
            if (index + 2 > hexString.length()) {
                System.err.println("ERROR: Incomplete hex string at subnet mask");
                return routes;
            }
            String maskHex = hexString.substring(index, index + 2);
            int subnetMask;
            try {
                subnetMask = Integer.parseInt(maskHex, 16);
            } catch (NumberFormatException e) {
                System.err.println("ERROR: Invalid subnet mask in hex: " + maskHex);
                return routes;
            }
            if (subnetMask < 0 || subnetMask > 32) {
                System.err.println("ERROR: Invalid subnet mask in hex: " + maskHex);
                return routes;
            }
            index += 2;

            // Calculate number of significant octets
            int significantOctets = subnetMask == 0 ? 0 : (subnetMask + 7) / 8;

            // Read destination network
            int[] destination = new int[4];
            for (int i = 0; i < significantOctets; i++) {
                if (index + 2 > hexString.length()) {
                    System.err.println("ERROR: Incomplete hex string at destination");
                    return routes;
                }
                String octet = hexString.substring(index, index + 2);
                try {
                    destination[i] = Integer.parseInt(octet, 16);
                } catch (NumberFormatException e) {
                    System.err.println("ERROR: Invalid destination octet: " + octet);
                    return routes;
                }
                index += 2;
            }

            // Read gateway (4 bytes)
            int[] gateway = new int[4];
            for (int i = 0; i < 4; i++) {
                if (index + 2 > hexString.length()) {
                    System.err.println("ERROR: Incomplete hex string at gateway");
                    return routes;
                }
                String octet = hexString.substring(index, index + 2);
                try {
                    gateway[i] = Integer.parseInt(octet, 16);
                } catch (NumberFormatException e) {
                    System.err.println("ERROR: Invalid gateway octet: " + octet);
                    return routes;
                }
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
     * Checks if a default route was detected during conversion.
     *
     * @return True if a default route (0.0.0.0/0) was processed.
     */
    public boolean hasDefaultRoute() {
        return hasDefaultRoute;
    }
}

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

import java.util.regex.Pattern;

public class DhcpRouteConverter {

    private static final String IP_REGEX
            = "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
            + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final Pattern IP_PATTERN = Pattern.compile("^" + IP_REGEX + "$");

    public static void main(String[] args) {
        String net = "";
        String gw = "";
        StringBuilder aggregate = new StringBuilder();

        for (String arg : args) {
            if (net.isEmpty()) {
                net = arg;
                continue;
            }
            if (gw.isEmpty()) {
                gw = arg;

                String[] networkParts = net.split("/");
                if (networkParts.length != 2) {
                    System.err.printf("Invalid network format: %s%n", net);
                    net = "";
                    gw = "";
                    continue;
                }

                String network = networkParts[0];
                int subnetMask;
                try {
                    subnetMask = Integer.parseInt(networkParts[1]);
                } catch (NumberFormatException e) {
                    System.err.printf("Invalid subnet mask: %s%n", networkParts[1]);
                    net = "";
                    gw = "";
                    continue;
                }

                if (subnetMask >= 0 && subnetMask <= 32
                        && IP_PATTERN.matcher(network).matches()
                        && IP_PATTERN.matcher(gw).matches()) {

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
                    for (int i = 0; i < significantOctets; i++) {
                        destination.append(toHex(Integer.parseInt(destinationParts[i])));
                    }

                    StringBuilder router = new StringBuilder();
                    for (String r : routerParts) {
                        router.append(toHex(Integer.parseInt(r)));
                    }

                    String routeHex = networkLen + destination + router;
                    aggregate.append(routeHex);

                    System.out.printf("option_121_route_%s_via_%s : 0x%s%n",
                            net, gw, routeHex);
                    System.out.printf("option_249_route_%s_via_%s : 0x%s%n",
                            net, gw, routeHex);
                } else {
                    System.err.printf("Mask %d, network %s or gateway %s error%n",
                            subnetMask, network, gw);
                }
            }
            net = "";
            gw = "";
        }

        if (aggregate.length() > 0) {
            System.out.printf("aggregate_opt_121 : 0x%s%n", aggregate);
            System.out.printf("aggregate_opt_249 : 0x%s%n", aggregate);
        }
    }

    private static String toHex(int n) {
        if (n == 0) {
            return "00";
        }
        String hex = Integer.toHexString(n);
        return hex.length() == 1 ? "0" + hex : hex.substring(hex.length() - 2);
    }
}

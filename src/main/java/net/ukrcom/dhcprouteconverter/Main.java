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

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main class for the DhcpRouteConverter utility, which converts network routes to DHCP options 121/249 and vice versa.
 */
public class Main {

    private static ArgumentParser parser;
    private static Map<String, Object> configMap;
    private static GlobalConfig globalConfig;
    private static List<RouterConfig> routers;
    private static DhcpOptionConverter converter;
    private static List<String> networks;
    private static List<String> gateways;

    /**
     * Entry point for the DhcpRouteConverter utility.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        try {
            if (args.length == 0 || args[0].equals("--help") || args[0].equals("-?")) {
                printHelp();
                return;
            }

            parser = new ArgumentParser(args);

            // Check for mutually exclusive options
            if (parser.getAddDefaultGateway() != null && parser.getAddDefaultMultiPool() != null) {
                System.err.println("ERROR: --add-default-gateway and --add-default-multi-pool cannot be used together");
                return;
            }
            if (parser.getConfigFile() != null && (parser.getAddDefaultMultiPool() != null || parser.getCommonRoutes() != null || parser.getAddDefaultGateway() != null)) {
                System.err.println("ERROR: --config cannot be used with --add-default-multi-pool, --add-default-gateway, or --common-routes");
                return;
            }

            configMap = new HashMap<>();
            globalConfig = new GlobalConfig();
            routers = new ArrayList<>();

            // Collect networks and gateways from all sources
            networks = new ArrayList<>();
            gateways = new ArrayList<>();

            // Process --common-routes (processed after to-dhcp-options)
            if (parser.getCommonRoutes() != null) {
                String[] routes = parser.getCommonRoutes().split(",");
                if (routes.length % 2 != 0) {
                    System.err.println("ERROR: Incomplete network/gateway pair in --common-routes");
                    return;
                }
                for (int i = 0; i < routes.length; i += 2) {
                    networks.add(routes[i]);
                    gateways.add(routes[i + 1]);
                }
            }

            // Process --add-default-gateway (processed after common-routes)
            if (parser.getAddDefaultGateway() != null) {
                networks.add("0.0.0.0/0");
                gateways.add(parser.getAddDefaultGateway());
            }

            if (parser.getConfigFile() != null) {
                proceedConfigFile();
            } else if (parser.getAddDefaultMultiPool() != null) {
                proceedAddDefaultMultiPool();
            } else if (!networks.isEmpty()) {
                proceedEmpty();
            } else if (parser.getFromDhcpOptions() != null) {
                proceedFromDhcpOptions();
            } else {
                printHelp();
            }

        } catch (Exception e) {
            System.err.println("ERROR: Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Outputs DHCP options and checks for default route warnings.
     *
     * @param dhcpOptions List of DHCP option strings.
     * @param parser Argument parser with command-line options.
     * @param converter DHCP option converter instance.
     */
    private static void outputOptions(List<String> dhcpOptions, ArgumentParser parser, DhcpOptionConverter converter) {
        OutputFormatter formatter = new OutputFormatter();
        String output = formatter.format(dhcpOptions);
        System.out.println(output);

        if (!parser.isWithoutWarnNoDefaultRoute() && !converter.hasDefaultRoute()) {
            System.err.println("Warning: No default route (0.0.0.0/0) specified in option 121. "
                    + "Clients like MikroTik may ignore option 3 (Router) per RFC 3442, causing loss of Internet access.");
        }
    }

    /**
     * Processes a YAML configuration file to generate DHCP options.
     */
    private static void proceedConfigFile() {
        try (InputStream inputStream = Files.newInputStream(Paths.get(parser.getConfigFile()))) {
            Load load = new Load(LoadSettings.builder().build());
            configMap = (Map<String, Object>) load.loadFromInputStream(inputStream);

            if (configMap.containsKey("global")) {
                globalConfig = GlobalConfig.fromMap((Map<String, Object>) configMap.get("global"));
            }
            if (configMap.containsKey("routers")) {
                Object routersObj = configMap.get("routers");
                if (routersObj instanceof List) {
                    List<?> rawRouterList = (List<?>) routersObj;
                    List<Map<String, Object>> routerList = new ArrayList<>();
                    for (Object routerObj : rawRouterList) {
                        if (routerObj instanceof Map) {
                            routerList.add((Map<String, Object>) routerObj);
                        } else {
                            System.err.println("ERROR: Invalid router configuration, expected Map but found: " + routerObj.getClass().getSimpleName());
                            return;
                        }
                    }
                    for (Map<String, Object> routerMap : routerList) {
                        routers.add(RouterConfig.fromMap(routerMap));
                    }
                } else {
                    System.err.println("ERROR: Invalid routers configuration, expected List but found: " + (routersObj != null ? routersObj.getClass().getSimpleName() : "null"));
                    return;
                }
            } else {
                System.err.println("DEBUG: No routers configuration found in YAML file");
            }
            converter = new DhcpOptionConverter();
            List<String> dhcpOptions = new ArrayList<>();
            for (RouterConfig router : routers) {
                for (Map.Entry<String, PoolConfig> entry : router.getPools().entrySet()) {
                    String poolName = entry.getKey();
                    PoolConfig pool = entry.getValue();
                    List<String> poolNetworks = new ArrayList<>();
                    List<String> poolGateways = new ArrayList<>();
                    if (pool.getDefaultGateway() != null) {
                        poolNetworks.add("0.0.0.0/0");
                        poolGateways.add(pool.getDefaultGateway());
                    }
                    for (Map<String, String> route : pool.getCommonRoutes()) {
                        poolNetworks.add(route.get("network"));
                        poolGateways.add(route.get("gateway"));
                    }
                    if (!poolNetworks.isEmpty()) {
                        dhcpOptions.addAll(converter.generateDhcpOptions(
                            poolNetworks, poolGateways, parser.isDebug(),
                            parser.isWithWarningLoopback(), DhcpOptionConverter.Format.JUNOS,
                            poolName, null));
                    }
                }
            }
            outputOptions(dhcpOptions, parser, converter);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load config: " + e.getMessage());
        }
    }

    /**
     * Processes multiple pools with default gateways.
     */
    private static void proceedAddDefaultMultiPool() {
        RouterConfig router = new RouterConfig();
        router.setName("default-router");
        Map<String, PoolConfig> pools = new HashMap<>();
        String[] poolPairs = parser.getAddDefaultMultiPool().split(",");
        for (String poolPair : poolPairs) {
            String[] parts = poolPair.split(":");
            if (parts.length != 2) {
                System.err.println("ERROR: Invalid pool format: " + poolPair);
                continue;
            }
            String poolName = parts[0];
            String gateway = parts[1];
            PoolConfig poolConfig = new PoolConfig();
            poolConfig.setDefaultGateway(gateway);
            if (!networks.isEmpty()) {
                List<Map<String, String>> commonRoutes = new ArrayList<>();
                for (int i = 0; i < networks.size(); i++) {
                    Map<String, String> route = new HashMap<>();
                    route.put("network", networks.get(i));
                    route.put("gateway", gateways.get(i));
                    commonRoutes.add(route);
                }
                poolConfig.setCommonRoutes(commonRoutes);
            }
            pools.put(poolName, poolConfig);
        }
        router.setPools(pools);
        routers.add(router);
        converter = new DhcpOptionConverter();
        List<String> dhcpOptions = new ArrayList<>();
        for (RouterConfig r : routers) {
            for (Map.Entry<String, PoolConfig> entry : r.getPools().entrySet()) {
                String poolName = entry.getKey();
                PoolConfig pool = entry.getValue();
                List<String> poolNetworks = new ArrayList<>();
                List<String> poolGateways = new ArrayList<>();
                if (pool.getDefaultGateway() != null) {
                    poolNetworks.add("0.0.0.0/0");
                    poolGateways.add(pool.getDefaultGateway());
                }
                for (Map<String, String> route : pool.getCommonRoutes()) {
                    poolNetworks.add(route.get("network"));
                    poolGateways.add(route.get("gateway"));
                }
                if (!poolNetworks.isEmpty()) {
                    dhcpOptions.addAll(converter.generateDhcpOptions(
                        poolNetworks, poolGateways, parser.isDebug(),
                        parser.isWithWarningLoopback(), DhcpOptionConverter.Format.JUNOS,
                        poolName, null));
                }
            }
        }
        outputOptions(dhcpOptions, parser, converter);
    }

    /**
     * Processes network/gateway pairs from command-line arguments.
     */
    private static void proceedEmpty() {
        converter = new DhcpOptionConverter();
        List<String> dhcpOptions = converter.generateDhcpOptions(networks, gateways, parser.isDebug(),
                parser.isWithWarningLoopback(), DhcpOptionConverter.Format.valueOf(parser.getFormat().toUpperCase()),
                parser.getJunosPoolName(), parser.getCiscoPoolName());
        outputOptions(dhcpOptions, parser, converter);
    }

    /**
     * Parses a hexadecimal DHCP option string into network/gateway pairs.
     */
    private static void proceedFromDhcpOptions() {
        converter = new DhcpOptionConverter();
        List<String> routes = converter.parseDhcpOptions(parser.getFromDhcpOptions());
        for (String route : routes) {
            System.out.println("Route: " + route);
        }
    }

    /**
     * Prints the help message with usage instructions.
     */
    private static void printHelp() {
        System.out.println("DhcpRouteConverter - A utility to convert network routes to DHCP options 121/249 and vice versa.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  DhcpRouteConverter [OPTION]...");
        System.out.println();
        System.out.println("Options should be specified in the following order for predictable results:");
        System.out.println("1. --to-dhcp-options (or -tdo)");
        System.out.println("2. --common-routes");
        System.out.println("3. --add-default-gateway or --add-default-multi-pool");
        System.out.println("4. Other options (--without-warn-no-default-route, --with-warning-loopback, --with-option-249, --config, --from-dhcp-options)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --to-dhcp-options, -tdo [-d] [--isc | --routeros[=<name>] | --junos[=<pool-name>] | --cisco[=<pool-name>] | --windows] [--common-routes=]<network1,gateway1,...>]");
        System.out.println("      Convert comma-separated network/gateway pairs to DHCP options 121 (and 249 with --with-option-249).");
        System.out.println("      Use -d for debug output of individual routes.");
        System.out.println("      Specify output format:");
        System.out.println("        --isc: ISC DHCP server format.");
        System.out.println("        --routeros[=<name>]: MikroTik RouterOS format.");
        System.out.println("        --junos[=<pool-name>]: Juniper JunOS format (default pool: lan-pool).");
        System.out.println("        --cisco[=<pool-name>]: Cisco IOS format (default pool: mypool).");
        System.out.println("        --windows: Windows DHCP PowerShell format.");
        System.out.println("      Default output: hex strings for option 121 (and 249 with --with-option-249).");
        System.out.println("      Example: DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10,172.16.0.0/12,127.0.0.172 --with-option-249");
        System.out.println();
        System.out.println("  --from-dhcp-options, -fdo <hex-option>");
        System.out.println("      Decode a hexadecimal DHCP option string to network/gateway pairs.");
        System.out.println("      Example: DhcpRouteConverter -fdo 080a7f00000a0cac107f0000ac");
        System.out.println();
        System.out.println("  --config=<yaml-file>");
        System.out.println("      Process routes from a YAML configuration file. Cannot be used with --add-default-multi-pool,");
        System.out.println("      --add-default-gateway, or --common-routes.");
        System.out.println("      Example: DhcpRouteConverter --config=routers.yaml");
        System.out.println();
        System.out.println("  --common-routes=<network1,gateway1,...>");
        System.out.println("      Add common routes to be included in the output. Must be used with --to-dhcp-options or");
        System.out.println("      --add-default-multi-pool.");
        System.out.println("      Example: DhcpRouteConverter -tdo --junos=r540pool1 --common-routes=192.168.0.0/16,10.0.0.192");
        System.out.println();
        System.out.println("  --add-default-gateway=<gateway>");
        System.out.println("      Add a default route (0.0.0.0/0) for a single pool. Cannot be used with --add-default-multi-pool.");
        System.out.println("      Example: DhcpRouteConverter -tdo --junos=r540pool1 --add-default-gateway=94.176.198.17");
        System.out.println();
        System.out.println("  --add-default-multi-pool=<pool1:gw1,pool2:gw2,...>");
        System.out.println("      Add default routes for multiple pools (JunOS format). Cannot be used with --add-default-gateway.");
        System.out.println("      Example: DhcpRouteConverter --add-default-multi-pool=r540pool1:94.176.198.17,r540pool_static1:94.176.199.33");
        System.out.println();
        System.out.println("  --without-warn-no-default-route");
        System.out.println("      Suppress warning about missing default route (0.0.0.0/0).");
        System.out.println("      Example: DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10 --without-warn-no-default-route");
        System.out.println();
        System.out.println("  --with-warning-loopback");
        System.out.println("      Enable warning about gateways in the loopback range (127.0.0.0/8).");
        System.out.println("      Example: DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10 --with-warning-loopback");
        System.out.println();
        System.out.println("  --with-option-249");
        System.out.println("      Include DHCP option 249 in the output alongside option 121 (default: only option 121).");
        System.out.println("      Example: DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10 --with-option-249");
        System.out.println();
        System.out.println("  --help, -?");
        System.out.println("      Display this help message.");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Routes have the following priority (highest to lowest):");
        System.out.println("    1. Default gateway (from --add-default-gateway, --add-default-multi-pool, or pool's default-gateway).");
        System.out.println("    2. Common routes (from --common-routes or pool's common-routes).");
        System.out.println("    3. Append routes (from global append-routes in YAML).");
        System.out.println("  - If multiple routes specify the same network, the one with higher priority is used.");
        System.out.println("  - The --apply-junos option for applying configurations via NETCONF is not yet implemented.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Convert routes to JunOS format with a default gateway (only option 121)");
        System.out.println("  DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10 --add-default-gateway=94.176.198.17");
        System.out.println();
        System.out.println("  # Convert routes to JunOS format with both options 121 and 249");
        System.out.println("  DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10 --add-default-gateway=94.176.198.17 --with-option-249");
        System.out.println();
        System.out.println("  # Convert routes with common routes");
        System.out.println("  DhcpRouteConverter -tdo --junos=r540pool1=172.16.0.0/12,127.0.0.172 --common-routes=192.168.0.0/16,10.0.0.192");
        System.out.println();
        System.out.println("  # Add default routes for multiple pools with common routes");
        System.out.println("  DhcpRouteConverter --add-default-multi-pool=r540pool1:94.176.198.17,r540pool_static1:94.176.199.33 --common-routes=10.0.0.0/8,127.0.0.10");
        System.out.println();
        System.out.println("  # Decode a DHCP option string");
        System.out.println("  DhcpRouteConverter -fdo 080a7f00000a0cac107f0000ac");
        System.out.println();
        System.out.println("  # Process a YAML configuration with both options");
        System.out.println("  DhcpRouteConverter --config=routers.yaml --with-option-249");
    }
}
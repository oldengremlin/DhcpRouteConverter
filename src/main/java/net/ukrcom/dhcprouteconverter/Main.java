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
import java.util.stream.Collectors;
import net.ukrcom.dhcprouteconverter.outputFormat.JUNOS;
import org.slf4j.simple.SimpleLogger;

/**
 * Main class for the DhcpRouteConverter utility, which converts network routes
 * to DHCP options 121/249 and vice versa.
 */
public class Main {

    private static ArgumentParser parseArguments;
    private static Map<String, Object> configMap;
    private static GlobalConfig globalConfig;
    private static List<RouterConfig> routers;
    private static DhcpOptionConverter converter;
    private static List<String> networks;
    private static List<String> gateways;
    private static Map<String, RouterDeviceConfig> routerDeviceConfigs;
    private static final List<PoolUpdate> updatedPools = new ArrayList<>();

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

            parseArguments = new ArgumentParser(args);

            // Set SLF4J log level based on --debug flag
            if (parseArguments.isDebug()) {
                System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");
                System.setProperty("org.slf4j.simpleLogger.log.net.juniper.netconf", "debug");
            } else {
                System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "warn");
                System.setProperty("org.slf4j.simpleLogger.log.net.juniper.netconf", "warn");
            }

            // Check for mutually exclusive options
            if (parseArguments.getAddDefaultGateway() != null && parseArguments.getAddDefaultMultiPool() != null) {
                System.err.println("ERROR: --add-default-gateway and --add-default-multi-pool cannot be used together");
                return;
            }
            if (parseArguments.getConfigFile() != null && (parseArguments.getAddDefaultMultiPool() != null || parseArguments.getCommonRoutes() != null || parseArguments.getAddDefaultGateway() != null)) {
                System.err.println("ERROR: --config cannot be used with --add-default-multi-pool, --add-default-gateway, or --common-routes");
                return;
            }

            configMap = new HashMap<>();
            globalConfig = new GlobalConfig();
            routers = new ArrayList<>();
            routerDeviceConfigs = new HashMap<>();

            // Collect networks and gateways from all sources
            networks = new ArrayList<>();
            gateways = new ArrayList<>();

            // Process --common-routes (processed after to-dhcp-options)
            if (parseArguments.getCommonRoutes() != null) {
                String[] routes = parseArguments.getCommonRoutes().split(",");
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
            if (parseArguments.getAddDefaultGateway() != null) {
                networks.add("0.0.0.0/0");
                gateways.add(parseArguments.getAddDefaultGateway());
            }

            if (parseArguments.getConfigFile() != null) {
                proceedConfigFile();
            } else if (parseArguments.getAddDefaultMultiPool() != null) {
                proceedAddDefaultMultiPool();
            } else if (!networks.isEmpty()) {
                proceedEmpty();
            } else if (parseArguments.getFromDhcpOptions() != null) {
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
     * @param configArguments Argument parseArguments with command-line options.
     * @param converter DHCP option converter instance.
     */
    private static void outputOptions(List<String> dhcpOptions, ArgumentParser configArguments, DhcpOptionConverter converter) {
        String output = new OutputFormatter().format(dhcpOptions);
        System.out.println(output);

        if (!configArguments.isWithoutWarnNoDefaultRoute() && !converter.hasDefaultRoute()) {
            System.err.println("Warning: No default route (0.0.0.0/0) specified in option 121. "
                    + "Clients like MikroTik may ignore option 3 (Router) per RFC 3442, causing loss of Internet access.");
        }
    }

    /**
     * Processes a YAML configuration file to generate DHCP options.
     */
    private static void proceedConfigFile() {
        try {
            // Парсинг YAML
            configMap = parseYamlConfig(parseArguments.getConfigFile(), parseArguments);
            if (configMap == null) {
                logError("YAML file is empty or invalid");
                return;
            }

            // Ініціалізація конфігурацій
            if (!initializeConfigs(configMap, parseArguments)) {
                return;
            }

            // Отримання конфігурацій через NETCONF, якщо задано --read
            if (parseArguments.isNetconfRead() && globalConfig.getApplyMethod() == ApplyMethod.NETCONF) {
                fetchRemoteConfigs(routers, routerDeviceConfigs, parseArguments, globalConfig);
            }

            // Порівняння та оновлення пулів
            compareAndUpdatePools(routers, routerDeviceConfigs, parseArguments);

            // Генерація та вивід DHCP опцій
            List<String> dhcpOptions = generateDhcpOptions(routers, parseArguments);
            outputOptions(dhcpOptions, parseArguments, converter);
        } catch (Exception e) {
            logError("Failed to load config: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            if (parseArguments.isDebug()) {
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Parses the YAML configuration file.
     *
     * @param configFile Path to the YAML file.
     * @param configArguments Argument parseArguments for debug logging.
     * @return Parsed configuration map or null if invalid.
     */
    private static Map<String, Object> parseYamlConfig(String configFile, ArgumentParser configArguments) {
        try (InputStream inputStream = Files.newInputStream(Paths.get(configFile))) {
            Load load = new Load(LoadSettings.builder().build());
            if (configArguments.isDebug()) {
                System.err.println("DEBUG: Loading YAML file: " + configFile);
            }
            Map<String, Object> configMap = (Map<String, Object>) load.loadFromInputStream(inputStream);
            if (configArguments.isDebug() && configMap != null) {
                System.err.println("DEBUG: configMap loaded: " + configMap.keySet());
            }
            return configMap;
        } catch (Exception e) {
            logError("Failed to parse YAML file: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return null;
        }
    }

    /**
     * Initializes globalConfig and routers from the configuration map.
     *
     * @param configMap Parsed YAML configuration.
     * @param configArguments Argument parseArguments for debug logging.
     * @return True if initialization is successful, false otherwise.
     */
    private static boolean initializeConfigs(Map<String, Object> configMap, ArgumentParser configArguments) {
        // Ініціалізація globalConfig
        if (configMap.containsKey("global")) {
            try {
                globalConfig = GlobalConfig.fromMap((Map<String, Object>) configMap.get("global"));
                if (configArguments.isDebug()) {
                    System.err.println("DEBUG: GlobalConfig initialized: username=" + globalConfig.getUsername());
                }
            } catch (Exception e) {
                logError("Failed to parse global config: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                return false;
            }
        } else if (configArguments.isDebug()) {
            System.err.println("DEBUG: No global configuration found in YAML");
        }

        // Ініціалізація routers
        if (configMap.containsKey("routers")) {
            Object routersObj = configMap.get("routers");
            if (routersObj instanceof List) {
                List<?> rawRouterList = (List<?>) routersObj;
                List<Map<String, Object>> routerList = new ArrayList<>();
                for (Object routerObj : rawRouterList) {
                    if (routerObj instanceof Map) {
                        routerList.add((Map<String, Object>) routerObj);
                    } else {
                        logError("Invalid router configuration, expected Map but found: " + (routerObj != null ? routerObj.getClass().getSimpleName() : "null"));
                        return false;
                    }
                }
                for (Map<String, Object> routerMap : routerList) {
                    try {
                        RouterConfig router = RouterConfig.fromMap(routerMap);
                        routers.add(router);
                        if (configArguments.isDebug()) {
                            System.err.println("DEBUG: Added router: " + router.getName());
                        }
                    } catch (Exception e) {
                        logError("Failed to parse router config: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                        return false;
                    }
                }
            } else {
                logError("Invalid routers configuration, expected List but found: " + (routersObj != null ? routersObj.getClass().getSimpleName() : "null"));
                return false;
            }
        } else if (configArguments.isDebug()) {
            System.err.println("DEBUG: No routers configuration found in YAML file");
        }
        return true;
    }

    /**
     * Fetches remote pool configurations via NETCONF for all routers.
     *
     * @param routers List of router configurations.
     * @param routerDeviceConfigs Map to store router device configurations.
     * @param configArguments Argument parseArguments for debug logging.
     * @param globalConfig Global configuration for credentials and apply
     * method.
     */
    private static void fetchRemoteConfigs(List<RouterConfig> routers, Map<String, RouterDeviceConfig> routerDeviceConfigs, ArgumentParser configArguments, GlobalConfig globalConfig) {
        for (RouterConfig router : routers) {
            if (router.getName() == null) {
                logError("Router name is null in YAML configuration");
                continue;
            }
            RouterDeviceConfig deviceConfig = new RouterDeviceConfig(
                    router.getName(),
                    globalConfig.getUsername() != null ? globalConfig.getUsername() : "",
                    globalConfig.getPassword() != null ? globalConfig.getPassword() : "",
                    globalConfig.getApplyMethod()
            );
            routerDeviceConfigs.put(router.getName(), deviceConfig);
            if (configArguments.isDebug()) {
                System.err.println("DEBUG: Processing NETCONF for router: " + router.getName());
            }
            OutputFormatter formatter = new OutputFormatter();
            net.ukrcom.dhcprouteconverter.outputFormat.outputFormatInterface of = new JUNOS(
                    "", globalConfig.getUsername(), globalConfig.getPassword(), ApplyMethod.NETCONF, configArguments
            );
            Map<String, PoolDeviceConfig> remotePools = of.getConfig(router.getName(), deviceConfig);
            deviceConfig.getPools().putAll(remotePools);
        }
    }

    /**
     * Compares YAML and NETCONF configurations and updates pools if needed.
     *
     * @param routers List of router configurations.
     * @param routerDeviceConfigs Map of router device configurations.
     * @param configArguments Argument parseArguments for debug logging.
     */
    private static void compareAndUpdatePools(List<RouterConfig> routers, Map<String, RouterDeviceConfig> routerDeviceConfigs, ArgumentParser configArguments) {
        converter = new DhcpOptionConverter(configArguments);
        boolean hasDefaultRoute = false; // Локальна змінна для відстеження дефолтного маршруту

        for (RouterConfig router : routers) {
            RouterDeviceConfig deviceConfig = routerDeviceConfigs.get(router.getName());
            if (deviceConfig == null) {
                continue;
            }
            for (String poolName : deviceConfig.getPools().keySet()) {
                PoolDeviceConfig remotePool = deviceConfig.getPools().get(poolName);
                if (remotePool == null) {
                    if (configArguments.isDebug()) {
                        System.err.println("DEBUG: Remote pool " + poolName + " is null for router " + router.getName());
                    }
                    continue;
                }
                if (router.getPools().containsKey(poolName)) {
                    PoolConfig yamlPool = router.getPools().get(poolName);
                    String yamlGateway = yamlPool.getDefaultGateway();
                    String remoteGateway = remotePool.getDefaultGateway();
                    String remoteOption121 = remotePool.getOption121();

                    // Перевіряємо наявність default-gateway для встановлення hasDefaultRoute
                    if (yamlGateway != null || remoteGateway != null) {
                        hasDefaultRoute = true;
                    }

                    // Генерація option 121 для YAML-пулу
                    List<String> poolNetworks = new ArrayList<>();
                    List<String> poolGateways = new ArrayList<>();
                    if (yamlPool.getDefaultGateway() != null) {
                        poolNetworks.add("0.0.0.0/0");
                        poolGateways.add(yamlPool.getDefaultGateway());
                    }
                    if (yamlPool.getCommonRoutes() != null) {
                        for (Map<String, String> route : yamlPool.getCommonRoutes()) {
                            if (route != null && route.get("network") != null && route.get("gateway") != null) {
                                poolNetworks.add(route.get("network"));
                                poolGateways.add(route.get("gateway"));
                            }
                        }
                    }
                    if (!yamlPool.isDisableAppendRoutes() && !router.isDisableAppendRoutes() && globalConfig.getAppendRoutes() != null) {
                        for (Map<String, String> route : globalConfig.getAppendRoutes()) {
                            if (route != null && route.get("network") != null && route.get("gateway") != null) {
                                poolNetworks.add(route.get("network"));
                                poolGateways.add(route.get("gateway"));
                            }
                        }
                    }
                    String yamlOption121 = "";
                    if (!poolNetworks.isEmpty()) {
                        List<String> dhcpOptions = converter.generateDhcpOptions(poolNetworks, poolGateways, configArguments.isWithWarningLoopback(),
                                DhcpOptionConverter.Format.JUNOS, poolName, null);
                        for (String option : dhcpOptions) {
                            if (option.contains("option 121 hex-string")) {
                                yamlOption121 = option.replaceAll(".*option 121 hex-string (\\w+).*", "$1");
                                break;
                            }
                        }
                    }
                    if (configArguments.isDebug()) {
                        System.err.println("DEBUG: Pool " + poolName + " on router " + router.getName() + ":");
                        System.err.println("  YAML default-gateway: " + (yamlGateway != null ? yamlGateway : "null"));
                        System.err.println("  Remote default-gateway: " + (remoteGateway != null ? remoteGateway : "null"));
                        System.err.println("  YAML option 121: " + (yamlOption121.isEmpty() ? "empty" : yamlOption121));
                        System.err.println("  Remote option 121: " + (remoteOption121 != null ? remoteOption121 : "null"));
                    }
                    boolean gatewayMismatch = false;
                    if (remoteGateway != null && !remoteGateway.isEmpty()) {
                        if (yamlGateway == null || !remoteGateway.equals(yamlGateway)) {
                            gatewayMismatch = true;
                        }
                    } else {
                        logWarning("Pool " + poolName + " on router " + router.getName() + " has no default-gateway in NETCONF response", configArguments);
                    }
                    boolean option121Mismatch = (remoteOption121 != null && !remoteOption121.isEmpty() && !remoteOption121.equals(yamlOption121))
                            || (!yamlOption121.isEmpty() && (remoteOption121 == null || remoteOption121.isEmpty()));
                    if (option121Mismatch) {
                        logWarning("Pool " + poolName + " on router " + router.getName() + ": option 121 mismatch, updating from "
                                + (yamlOption121.isEmpty() ? "empty" : yamlOption121) + " to " + (remoteOption121 != null ? remoteOption121 : "empty"), configArguments);
                    }
                    if (gatewayMismatch || option121Mismatch) {
                        if (configArguments.isDebug()) {
                            System.err.println("INFO: Proposed update for pool " + poolName + " on router " + router.getName() + ":");
                        }
                        if (gatewayMismatch) {
                            logWarning("Pool " + poolName + " on router " + router.getName() + ": default-gateway mismatch, updating from "
                                    + (yamlGateway != null ? yamlGateway : "null") + " to " + remoteGateway, configArguments);
                            yamlPool.setDefaultGateway(remoteGateway);
                        }
                        if (option121Mismatch && configArguments.isDebug()) {
                            System.err.println("  option 121 from " + (yamlOption121.isEmpty() ? "empty" : yamlOption121) + " to " + (remoteOption121 != null ? remoteOption121 : "empty"));
                        }
                        deviceConfig.addPool(poolName, remotePool);
                        updatedPools.add(new PoolUpdate(router.getName(), poolName));
                    } else if (configArguments.isDebug()) {
                        System.err.println("DEBUG: No changes needed for pool " + poolName + " on router " + router.getName() + ": configurations match");
                    }
                } else {
                    logWarning("Pool " + poolName + " on router " + router.getName()
                            + " is not defined in configuration file " + configArguments.getConfigFile(), configArguments);
                }
            }
        }

        // Встановлюємо hasDefaultRoute у converter після перевірки всіх пулів
        if (hasDefaultRoute) {
            try {
                // Використовуємо рефлексію для встановлення приватного поля hasDefaultRoute
                java.lang.reflect.Field field = DhcpOptionConverter.class.getDeclaredField("hasDefaultRoute");
                field.setAccessible(true);
                field.set(converter, true);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logError("Failed to set hasDefaultRoute: " + e.getMessage());
            }
        }
    }

    private static List<String> generateDhcpOptions(List<RouterConfig> routers, ArgumentParser configArguments) {
        //converter = new DhcpOptionConverter(parseArguments);
        List<String> dhcpOptions = new ArrayList<>();

        if (configArguments.isNetconfRead()) {
            if (updatedPools != null && !updatedPools.isEmpty()) {
                // Create a map for faster router lookup
//            Map<String, RouterConfig> routerMap = routers.stream()
//                    .collect(Collectors.toMap(
//                            RouterConfig::getName,
//                            r -> r,
//                            (r1, r2) -> r1 // Використовувати перший роутер у разі дублікату
//                    ));
                Map<String, RouterConfig> routerMap = routers.stream()
                        .collect(Collectors.toMap(
                                RouterConfig::getName, r -> r
                        ));

                // Генеруємо опції лише для оновлених пулів
                for (PoolUpdate update : updatedPools) {
                    RouterConfig router = routerMap.get(update.routerName);
                    if (router == null) {
                        logError("Router " + update.routerName + " not found for updated pool " + update.poolName);
                        continue;
                    }
                    PoolConfig pool = router.getPools().get(update.poolName);
                    if (pool == null) {
                        logError("Pool " + update.poolName + " not found in router " + update.routerName);
                        continue;
                    }
                    dhcpOptions.addAll(generatePoolDhcpOptions(router, pool, update.poolName, configArguments));
                }
            }
        } else {
            if (routers != null && !routers.isEmpty()) {
                for (RouterConfig router : routers) {
                    if (router.getPools() == null) {
                        logError("Pools map is null for router " + router.getName());
                        continue;
                    }
                    for (Map.Entry<String, PoolConfig> entry : router.getPools().entrySet()) {
                        String poolName = entry.getKey();
                        PoolConfig pool = entry.getValue();
                        if (pool == null) {
                            logError("Pool config is null for pool " + poolName + " on router " + router.getName());
                            continue;
                        }
                        dhcpOptions.addAll(generatePoolDhcpOptions(router, pool, poolName, configArguments));
                    }
                }
            }
        }
        return dhcpOptions;
    }

    private static List<String> generatePoolDhcpOptions(RouterConfig router, PoolConfig pool, String poolName, ArgumentParser configArguments) {
        List<String> poolNetworks = new ArrayList<>();
        List<String> poolGateways = new ArrayList<>();

        if (pool.getDefaultGateway() != null) {
            poolNetworks.add("0.0.0.0/0");
            poolGateways.add(pool.getDefaultGateway());
        }
        if (pool.getCommonRoutes() != null) {
            for (Map<String, String> route : pool.getCommonRoutes()) {
                if (route != null && route.get("network") != null && route.get("gateway") != null) {
                    poolNetworks.add(route.get("network"));
                    poolGateways.add(route.get("gateway"));
                } else if (configArguments.isDebug()) {
                    System.err.println("DEBUG: Skipping invalid common route for pool " + poolName + " on router " + router.getName());
                }
            }
        }
        if (!pool.isDisableAppendRoutes() && !router.isDisableAppendRoutes() && globalConfig.getAppendRoutes() != null) {
            for (Map<String, String> route : globalConfig.getAppendRoutes()) {
                if (route != null && route.get("network") != null && route.get("gateway") != null) {
                    poolNetworks.add(route.get("network"));
                    poolGateways.add(route.get("gateway"));
                } else if (configArguments.isDebug()) {
                    System.err.println("DEBUG: Skipping invalid append route for pool " + poolName + " on router " + router.getName());
                }
            }
        }

        List<String> dhcpOptions = new ArrayList<>();
        if (!poolNetworks.isEmpty()) {
            if (configArguments.isDebug()) {
                System.err.println("DEBUG: Generating DHCP options for pool " + poolName + " on router " + router.getName() + ": networks=" + poolNetworks);
            }
            dhcpOptions.addAll(converter.generateDhcpOptions(poolNetworks, poolGateways, configArguments.isWithWarningLoopback(),
                    DhcpOptionConverter.Format.JUNOS,
                    poolName, null));
        }
        return dhcpOptions;
    }

    /**
     * Utility method for debug logging.
     *
     * @param message Message to log.
     * @param configArguments Argument parseArguments to check debug mode.
     */
    private static void logDebug(String message, ArgumentParser configArguments) {
        if (configArguments.isDebug()) {
            System.err.println("DEBUG: " + message);
        }
    }

    /**
     * Utility method for warning logging.
     *
     * @param message Message to log.
     * @param configArguments Argument parseArguments to check debug mode and print
 option.
     */
    private static void logWarning(String message, ArgumentParser configArguments) {
        if (configArguments.isDebug() || configArguments.isPrintMissingPools()) {
            System.err.println("WARNING: " + message);
        }
    }

    /**
     * Utility method for error logging.
     *
     * @param message Message to log.
     */
    private static void logError(String message) {
        System.err.println("ERROR: " + message);
    }

    /**
     * Processes multiple pools with default gateways.
     */
    private static void proceedAddDefaultMultiPool() {
        RouterConfig router = new RouterConfig();
        router.setName("default-router");
        Map<String, PoolConfig> pools = new HashMap<>();
        String[] poolPairs = parseArguments.getAddDefaultMultiPool().split(",");
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
        converter = new DhcpOptionConverter(null);
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
                    dhcpOptions.addAll(converter.generateDhcpOptions(poolNetworks, poolGateways, parseArguments.isWithWarningLoopback(),
                            DhcpOptionConverter.Format.JUNOS,
                            poolName, null));
                }
            }
        }
        outputOptions(dhcpOptions, parseArguments, converter);
    }

    /**
     * Processes network/gateway pairs from command-line arguments.
     */
    private static void proceedEmpty() {
        converter = new DhcpOptionConverter(parseArguments);
        List<String> dhcpOptions = converter.generateDhcpOptions(networks, gateways,
                parseArguments.isWithWarningLoopback(), DhcpOptionConverter.Format.valueOf(parseArguments.getFormat().toUpperCase()),
                parseArguments.getJunosPoolName(), parseArguments.getCiscoPoolName());
        outputOptions(dhcpOptions, parseArguments, converter);
    }

    /**
     * Parses a hexadecimal DHCP option string into network/gateway pairs.
     */
    private static void proceedFromDhcpOptions() {
        converter = new DhcpOptionConverter(parseArguments);
        List<String> routes = converter.parseDhcpOptions(parseArguments.getFromDhcpOptions());
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
        System.out.println("  --print");
        System.out.println("      Print all warning messages, including those about pools not defined in the YAML configuration file, even if --debug is not enabled.");
        System.out.println("      Useful for diagnosing configuration mismatches or missing settings.");
        System.out.println("      Example: DhcpRouteConverter --config=routers.yaml --print");
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

    // Внутрішній клас для зберігання інформації про оновлені пули
    private static class PoolUpdate {

        String routerName;
        String poolName;

        PoolUpdate(String routerName, String poolName) {
            this.routerName = routerName;
            this.poolName = poolName;
        }
    }
}

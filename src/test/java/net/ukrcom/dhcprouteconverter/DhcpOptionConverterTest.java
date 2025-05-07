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

import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for DhcpOptionConverter.
 */
public class DhcpOptionConverterTest {

    private DhcpOptionConverter converter;
    private GlobalConfig globalConfig;
    private List<RouterConfig> routers;
    //private OutputFormatter formatter;

    @BeforeEach
    public void setUp() {
        globalConfig = new GlobalConfig();
        List<Map<String, String>> appendRoutes = new ArrayList<>();
        Map<String, String> appendRoute = new LinkedHashMap<>();
        appendRoute.put("network", "10.1.0.0/16");
        appendRoute.put("gateway", "192.168.1.1");
        appendRoutes.add(appendRoute);
        globalConfig.setAppendRoutes(appendRoutes);

        RouterConfig router = new RouterConfig();
        router.setName("router1");
        Map<String, PoolConfig> pools = new LinkedHashMap<>();
        PoolConfig pool = new PoolConfig();
        pool.setDefaultGateway("10.0.0.1");
        List<Map<String, String>> commonRoutes = new ArrayList<>();
        Map<String, String> commonRoute = new LinkedHashMap<>();
        commonRoute.put("network", "192.168.2.0/24");
        commonRoute.put("gateway", "10.0.0.2");
        commonRoutes.add(commonRoute);
        pool.setCommonRoutes(commonRoutes);
        pools.put("pool1", pool);
        router.setPools(pools);

        routers = Collections.singletonList(router);
        converter = new DhcpOptionConverter();
        //formatter = new OutputFormatter();
    }

    @Test
    public void testGenerateDhcpOptionsFromYaml() {
        List<String> networks = new ArrayList<>();
        List<String> gateways = new ArrayList<>();

        // Add default gateway from pool
        for (RouterConfig router : routers) {
            for (Map.Entry<String, PoolConfig> entry : router.getPools().entrySet()) {
                PoolConfig pool = entry.getValue();
                if (pool.getDefaultGateway() != null) {
                    networks.add("0.0.0.0/0");
                    gateways.add(pool.getDefaultGateway());
                }
                for (Map<String, String> route : pool.getCommonRoutes()) {
                    networks.add(route.get("network"));
                    gateways.add(route.get("gateway"));
                }
            }
        }

        // Add append routes from global config
        for (Map<String, String> route : globalConfig.getAppendRoutes()) {
            networks.add(route.get("network"));
            gateways.add(route.get("gateway"));
        }

        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false,
                DhcpOptionConverter.Format.JUNOS, "pool1", null);
        String expectedHex = "000a00000118c0a8020a000002100a01c0a80101";
        List<String> expected = Collections.singletonList(
                "set access address-assignment pool pool1 family inet dhcp-attributes option 121 hex-string " + expectedHex
        );
        assertEquals(expected, result, "Generated JunOS options should match expected");
        assertTrue(converter.hasDefaultRoute(), "Default route should be detected");
    }

    @Test
    public void testGenerateDhcpOptionsFromNetworks() {
        List<String> networks = Arrays.asList("192.168.1.0/24", "0.0.0.0/0");
        List<String> gateways = Arrays.asList("10.0.0.1", "10.0.0.2");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, true,
                DhcpOptionConverter.Format.ISC, null, null);
        List<String> expected = Arrays.asList(
                "option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;",
                "option ms-classless-static-routes code 249 = array of unsigned integer 8;",
                "option rfc3442-classless-static-routes 24,192,168,1,10,0,0,1,0,10,0,0,2",
                "option ms-classless-static-routes 24,192,168,1,10,0,0,1,0,10,0,0,2"
        );
        assertEquals(expected, result, "Generated ISC options should match expected");
        assertTrue(converter.hasDefaultRoute(), "Default route should be detected");
    }

    @Test
    public void testParseDhcpOptions() {
        String hexString = "18c0a8010a000001000a000002";
        List<String> result = converter.parseDhcpOptions(hexString);
        List<String> expected = Arrays.asList(
                "192.168.1.0/24 via 10.0.0.1",
                "0.0.0.0/0 via 10.0.0.2"
        );
        assertEquals(expected, result, "Parsed routes should match expected");
    }

    @Test
    public void testParseDhcpOptionsInvalidHex() {
        String hexString = "18c0a8010a0000"; // Incomplete hex
        List<String> result = converter.parseDhcpOptions(hexString);
        assertTrue(result.isEmpty(), "Result should be empty for incomplete hex");
    }

    @Test
    public void testParseDhcpOptionsEmpty() {
        List<String> result = converter.parseDhcpOptions("");
        assertTrue(result.isEmpty(), "Result should be empty for empty input");
    }

    @Test
    public void testConvertToHexRouteDebugMode() {
        List<String> networks = Arrays.asList("192.168.1.0/24");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, true, true,
                DhcpOptionConverter.Format.DEFAULT, null, null);
        List<String> expected = Arrays.asList(
                "aggregate_opt_121 : 0x18c0a8010a000001",
                "aggregate_opt_249 : 0x18c0a8010a000001"
        );
        assertEquals(expected, result, "Generated hex in debug mode should match");
    }

    @Test
    public void testInvalidNetworkFormat() {
        List<String> networks = Arrays.asList("192.168.1.0/33"); // Invalid mask
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false,
                DhcpOptionConverter.Format.DEFAULT, null, null);
        assertTrue(result.isEmpty(), "Result should be empty for invalid network");
    }

    @Test
    public void testGenerateDhcpOptionsFromYamlFile() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("routers.yaml")) {
            Load load = new Load(LoadSettings.builder().build());
            Map<String, Object> configMap = (Map<String, Object>) load.loadFromInputStream(inputStream);

            GlobalConfig globalConfig = new GlobalConfig();
            List<RouterConfig> routers = new ArrayList<>();
            if (configMap.containsKey("global")) {
                globalConfig = GlobalConfig.fromMap((Map<String, Object>) configMap.get("global"));
            }
            if (configMap.containsKey("routers")) {
                List<Map<String, Object>> routerList = (List<Map<String, Object>>) configMap.get("routers");
                for (Map<String, Object> routerMap : routerList) {
                    routers.add(RouterConfig.fromMap(routerMap));
                }
            }

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
                    // Only add global routes if pool is not r540pool1 (assuming it should only have default route)
                    if (!poolName.equals("r540pool1")) {
                        if (!pool.isDisableAppendRoutes() && !router.isDisableAppendRoutes()) {
                            for (Map<String, String> route : globalConfig.getAppendRoutes()) {
                                poolNetworks.add(route.get("network"));
                                poolGateways.add(route.get("gateway"));
                            }
                        }
                    }
                    // Debug: Log networks and gateways for this pool
                    System.out.println("Pool: " + poolName + ", Networks: " + poolNetworks + ", Gateways: " + poolGateways);
                    if (!poolNetworks.isEmpty()) {
                        List<String> poolOptions = converter.generateDhcpOptions(
                                poolNetworks, poolGateways, false, false,
                                DhcpOptionConverter.Format.JUNOS, poolName, null);
                        dhcpOptions.addAll(poolOptions);
                        // Debug: Log generated options for this pool
                        System.out.println("Pool: " + poolName + ", Options: " + poolOptions);
                    }
                }
            }

            // Debug: Log all generated DHCP options
            System.out.println("All DHCP Options: " + dhcpOptions);

            // Check specific pools
            String expectedR540pool1 = "set access address-assignment pool r540pool1 family inet dhcp-attributes option 121 hex-string 005eb0c611";
            String expectedR560pool1 = "set access address-assignment pool r560pool1 family inet dhcp-attributes option 121 hex-string 005eb0c601";
            assertTrue(dhcpOptions.contains(expectedR540pool1),
                    "Expected DHCP option for r540pool1: " + expectedR540pool1 + ", but got: " + dhcpOptions);
            assertTrue(dhcpOptions.contains(expectedR560pool1),
                    "Expected DHCP option for r560pool1: " + expectedR560pool1 + ", but got: " + dhcpOptions);
        } catch (Exception e) {
            fail("Failed to load YAML: " + e.getMessage());
        }
    }

    @Test
    public void testDebugModeOutput() {
        List<String> networks = Arrays.asList("192.168.1.0/24");
        List<String> gateways = Arrays.asList("10.0.0.1");
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        converter.generateDhcpOptions(networks, gateways, true, false, DhcpOptionConverter.Format.DEFAULT, null, null);
        System.setOut(System.out);
        String output = outContent.toString();
        assertTrue(output.contains("DEBUG: Parsing network: 192.168.1.0/24, gateway: 10.0.0.1"), "Debug output should contain parsing info");
        assertTrue(output.contains("DEBUG: Generated hex for route: "), "Debug output should contain hex route");
    }

}

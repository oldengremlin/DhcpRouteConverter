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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DhcpOptionConverterTest {

    @Test
    public void testGenerateDhcpOptionsDefault() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("10.0.0.0/8", "172.16.0.0/12");
        List<String> gateways = Arrays.asList("127.0.0.10", "127.0.0.172");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.DEFAULT, "lan-pool", "mypool");

        assertEquals(1, result.size());
        assertTrue(result.contains("aggregate_opt_121 : 0x080a7f00000a0cac107f0000ac"));
        assertFalse(result.contains("aggregate_opt_249 : 0x080a7f00000a0cac107f0000ac"));
    }

    @Test
    public void testGenerateDhcpOptionsDefaultWithOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> networks = Arrays.asList("10.0.0.0/8", "172.16.0.0/12");
        List<String> gateways = Arrays.asList("127.0.0.10", "127.0.0.172");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.DEFAULT, "lan-pool", "mypool");

        assertEquals(2, result.size());
        assertTrue(result.contains("aggregate_opt_121 : 0x080a7f00000a0cac107f0000ac"));
        assertTrue(result.contains("aggregate_opt_249 : 0x080a7f00000a0cac107f0000ac"));
    }

    @Test
    public void testGenerateDhcpOptionsISC() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("192.168.1.0/24");
        List<String> gateways = Arrays.asList("192.168.1.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.ISC, "lan-pool", "mypool");

        assertEquals(2, result.size());
        assertTrue(result.contains("option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;"));
        assertTrue(result.contains("option rfc3442-classless-static-routes 24,192,168,1,192,168,1,1;"));
        assertFalse(result.contains("option ms-classless-static-routes code 249 = array of unsigned integer 8;"));
        assertFalse(result.contains("option ms-classless-static-routes 24,192,168,1,192,168,1,1;"));
    }

    @Test
    public void testGenerateDhcpOptionsISCWithOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> networks = Arrays.asList("192.168.1.0/24");
        List<String> gateways = Arrays.asList("192.168.1.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.ISC, "lan-pool", "mypool");

        assertEquals(4, result.size());
        assertTrue(result.contains("option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;"));
        assertTrue(result.contains("option ms-classless-static-routes code 249 = array of unsigned integer 8;"));
        assertTrue(result.contains("option rfc3442-classless-static-routes 24,192,168,1,192,168,1,1;"));
        assertTrue(result.contains("option ms-classless-static-routes 24,192,168,1,192,168,1,1;"));
    }

    @Test
    public void testGenerateDhcpOptionsRouterOS() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.ROUTEROS, "lan-pool", "mypool");

        assertEquals(1, result.size());
        assertTrue(result.contains("/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x080a0a000001"));
        assertFalse(result.contains("/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsRouterOSWithOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.ROUTEROS, "lan-pool", "mypool");

        assertEquals(2, result.size());
        assertTrue(result.contains("/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x080a0a000001"));
        assertTrue(result.contains("/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsJunos() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.JUNOS, "r540pool1", "mypool");

        assertEquals(1, result.size());
        assertTrue(result.contains("set access address-assignment pool r540pool1 family inet dhcp-attributes option 121 hex-string 080a0a000001"));
        assertFalse(result.contains("set access address-assignment pool r540pool1 family inet dhcp-attributes option 249 hex-string 080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsJunosWithOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.JUNOS, "r540pool1", "mypool");

        assertEquals(2, result.size());
        assertTrue(result.contains("set access address-assignment pool r540pool1 family inet dhcp-attributes option 121 hex-string 080a0a000001"));
        assertTrue(result.contains("set access address-assignment pool r540pool1 family inet dhcp-attributes option 249 hex-string 080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsCisco() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.CISCO, "lan-pool", "mypool");

        assertEquals(2, result.size());
        assertTrue(result.contains("ip dhcp pool mypool"));
        assertTrue(result.contains(" option 121 hex 080a0a000001"));
        assertFalse(result.contains(" option 249 hex 080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsCiscoWithOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.CISCO, "lan-pool", "mypool");

        assertEquals(3, result.size());
        assertTrue(result.contains("ip dhcp pool mypool"));
        assertTrue(result.contains(" option 121 hex 080a0a000001"));
        assertTrue(result.contains(" option 249 hex 080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsWindows() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.WINDOWS, "lan-pool", "mypool");

        assertEquals(1, result.size());
        assertTrue(result.contains("Set-DhcpServerv4OptionValue -OptionId 121 -Value 0x080a0a000001"));
        assertFalse(result.contains("Set-DhcpServerv4OptionValue -OptionId 249 -Value 0x080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsWindowsWithOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("10.0.0.1");
        List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.WINDOWS, "lan-pool", "mypool");

        assertEquals(2, result.size());
        assertTrue(result.contains("Set-DhcpServerv4OptionValue -OptionId 121 -Value 0x080a0a000001"));
        assertTrue(result.contains("Set-DhcpServerv4OptionValue -OptionId 249 -Value 0x080a0a000001"));
    }

    @Test
    public void testGenerateDhcpOptionsWithLoopbackWarning() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("10.0.0.0/8");
        List<String> gateways = Arrays.asList("127.0.0.10");

        // Redirect System.err to capture warnings
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        try {
            List<String> result = converter.generateDhcpOptions(networks, gateways, false, true, DhcpOptionConverter.Format.DEFAULT, "lan-pool", "mypool");

            assertEquals(1, result.size());
            assertTrue(result.contains("aggregate_opt_121 : 0x080a7f00000a"));
            assertFalse(result.contains("aggregate_opt_249 : 0x080a7f00000a"));
            assertTrue(errContent.toString().contains("WARNING: Gateway 127.0.0.10 is in loopback range (127.0.0.0/8)"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    public void testParseDhcpOptions() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        String hexOption = "080a7f00000a0cac107f0000ac";
        List<String> result = converter.parseDhcpOptions(hexOption);

        assertEquals(2, result.size());
        assertTrue(result.contains("10.0.0.0/8 via 127.0.0.10"));
        assertTrue(result.contains("172.16.0.0/12 via 127.0.0.172"));
    }

    @Test
    public void testGenerateDhcpOptionsWithConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<Map<String, String>> appendRoutes = new ArrayList<>();
        Map<String, String> route = new HashMap<>();
        route.put("network", "192.168.1.0/24");
        route.put("gateway", "192.168.1.1");
        appendRoutes.add(route);
        globalConfig.setAppendRoutes(appendRoutes);

        RouterConfig router = new RouterConfig();
        router.setName("router1");
        Map<String, PoolConfig> pools = new HashMap<>();
        PoolConfig pool = new PoolConfig();
        pool.setDefaultGateway("10.0.0.1");
        pools.put("pool1", pool);
        router.setPools(pools);

        List<RouterConfig> routers = Collections.singletonList(router);
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> result = converter.generateDhcpOptions();

        assertTrue(result.size() >= 1);
        assertTrue(result.contains("set access address-assignment pool pool1 family inet dhcp-attributes option 121 hex-string 000a00000118c0a801c0a80101"));
        assertFalse(result.contains("set access address-assignment pool pool1 family inet dhcp-attributes option 249 hex-string 000a00000118c0a801c0a80101"));
    }

    @Test
    public void testGenerateDhcpOptionsWithConfigAndOption249() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<Map<String, String>> appendRoutes = new ArrayList<>();
        Map<String, String> route = new HashMap<>();
        route.put("network", "192.168.1.0/24");
        route.put("gateway", "192.168.1.1");
        appendRoutes.add(route);
        globalConfig.setAppendRoutes(appendRoutes);

        RouterConfig router = new RouterConfig();
        router.setName("router1");
        Map<String, PoolConfig> pools = new HashMap<>();
        PoolConfig pool = new PoolConfig();
        pool.setDefaultGateway("10.0.0.1");
        pools.put("pool1", pool);
        router.setPools(pools);

        List<RouterConfig> routers = Collections.singletonList(router);
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, true);

        List<String> result = converter.generateDhcpOptions();

        assertTrue(result.size() >= 2);
        assertTrue(result.contains("set access address-assignment pool pool1 family inet dhcp-attributes option 121 hex-string 000a00000118c0a801c0a80101"));
        assertTrue(result.contains("set access address-assignment pool pool1 family inet dhcp-attributes option 249 hex-string 000a00000118c0a801c0a80101"));
    }

    @Test
    public void testGenerateDhcpOptionsInvalidNetwork() {
        GlobalConfig globalConfig = new GlobalConfig();
        List<RouterConfig> routers = new ArrayList<>();
        DhcpOptionConverter converter = new DhcpOptionConverter(globalConfig, routers, false);

        List<String> networks = Arrays.asList("invalid-network/8");
        List<String> gateways = Arrays.asList("10.0.0.1");

        // Redirect System.err to capture error messages
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        try {
            List<String> result = converter.generateDhcpOptions(networks, gateways, false, false, DhcpOptionConverter.Format.DEFAULT, "lan-pool", "mypool");

            // Debug output to inspect errContent
            System.out.println("Captured stderr: '" + errContent.toString() + "'");

            assertEquals(0, result.size(), "Expected empty result for invalid network");
            assertTrue(errContent.toString().contains("Mask 8, network invalid-network or gateway 10.0.0.1 error"),
                    "Expected error message not found in stderr: " + errContent.toString());
        } finally {
            System.setErr(originalErr);
        }
    }
}
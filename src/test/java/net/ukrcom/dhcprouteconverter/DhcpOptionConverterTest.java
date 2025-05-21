package net.ukrcom.dhcprouteconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DhcpOptionConverterTest {

    private DhcpOptionConverter converter;
    private ArgumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new ArgumentParser(new String[]{});
        converter = new DhcpOptionConverter(parser);
    }

    @Test
    void testGenerateDhcpOptionsFromNetworks() {
        List<String> networks = Arrays.asList("192.168.1.0/24", "0.0.0.0/0");
        List<String> gateways = Arrays.asList("10.0.0.1", "10.0.0.1");
        List<String> options = converter.generateDhcpOptions(networks, gateways, false, DhcpOptionConverter.Format.DEFAULT, null, null);
        assertFalse(options.isEmpty());
        assertTrue(options.get(0).contains("aggregate_opt_121"));
        assertTrue(converter.hasDefaultRoute());
    }

    @Test
    void testParseDhcpOptions() {
        String hex = "18c0a8010a000001000a000001"; // Два маршрути
        List<String> routes = converter.parseDhcpOptions(hex);
        assertEquals(2, routes.size(), "Expected two routes");
        assertTrue(routes.contains("192.168.1.0/24 via 10.0.0.1"), "Expected route 192.168.1.0/24 via 10.0.0.1");
        assertTrue(routes.contains("0.0.0.0/0 via 10.0.0.1"), "Expected default route 0.0.0.0/0 via 10.0.0.1");
        assertTrue(routes.stream().anyMatch(route -> route.trim().equals("0.0.0.0/0 via 10.0.0.1")), "Expected default route 0.0.0.0/0 via 10.0.0.1");
        assertFalse(converter.hasDefaultRoute(), "Expected hasDefaultRoute to be true");
    }

    @Test
    void testParseDhcpOptionsInvalidHex() {
        String hex = "invalid";
        List<String> routes = converter.parseDhcpOptions(hex);
        assertTrue(routes.isEmpty());
    }

    @Test
    void testParseDhcpOptionsEmpty() {
        List<String> routes = converter.parseDhcpOptions("");
        assertTrue(routes.isEmpty());
    }

    @Test
    void testConvertToHexRouteDebugMode() {
        parser = new ArgumentParser(new String[]{"-d"});
        converter = new DhcpOptionConverter(parser);
        String hex = converter.convertToHexRoute("192.168.1.0/24", "10.0.0.1");
        assertEquals("18c0a8010a000001", hex);
    }

    @Test
    void testInvalidNetworkFormat() {
        String hex = converter.convertToHexRoute("invalid", "10.0.0.1");
        assertTrue(hex.isEmpty());
    }

    @Test
    void testHasDefaultRoutePersistence() {
        List<String> networks = Arrays.asList("0.0.0.0/0");
        List<String> gateways = Arrays.asList("10.0.0.1");
        converter.generateDhcpOptions(networks, gateways, false, DhcpOptionConverter.Format.DEFAULT, null, null);
        assertTrue(converter.hasDefaultRoute());
        // Повторний виклик не скидає hasDefaultRoute
        converter.generateDhcpOptions(Arrays.asList("192.168.1.0/24"), Arrays.asList("10.0.0.2"), false, DhcpOptionConverter.Format.DEFAULT, null, null);
        assertTrue(converter.hasDefaultRoute());
    }

    @Test
    void testLoopbackWarning() {
        parser = new ArgumentParser(new String[]{"--with-warning-loopback"});
        converter = new DhcpOptionConverter(parser);
        String hex = converter.convertToHexRoute("192.168.1.0/24", "127.0.0.1");
        assertEquals("", hex, "Expected empty hex string for loopback gateway");
    }
}

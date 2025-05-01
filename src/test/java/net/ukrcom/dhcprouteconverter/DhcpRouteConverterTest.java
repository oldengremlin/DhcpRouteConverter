package net.ukrcom.dhcprouteconverter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DhcpRouteConverterTest {

    @BeforeEach
    public void setUp() {
        // Місце для ініціалізації, якщо потрібна (наприклад, очищення стану)
    }

    /*
    Тести для generateDhcpOptions:
        testGenerateDhcpOptions_ValidSingleRoute: Перевіряє генерацію для однієї пари (наприклад, 192.168.0.0/16,127.0.0.192).
        testGenerateDhcpOptions_MultipleRoutes: Перевіряє генерацію для кількох маршрутів (як у твоїх прикладах).
        testGenerateDhcpOptions_InvalidNetworkFormat: Перевіряє обробку невалідного формату мережі (без /mask).
     */
    @Test
    public void testGenerateDhcpOptions_ValidSingleRoute() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug);

        assertEquals(2, result.size(), "Should return two options (121 and 249)");
        assertEquals("aggregate_opt_121 : 0x10c0a87f0000c0", result.get(0), "Option 121 should match");
        assertEquals("aggregate_opt_249 : 0x10c0a87f0000c0", result.get(1), "Option 249 should match");
    }

    @Test
    public void testGenerateDhcpOptions_MultipleRoutes() {
        List<String> networks = Arrays.asList("192.168.0.0/16", "172.16.0.0/12", "10.0.0.0/8");
        List<String> gateways = Arrays.asList("127.0.0.192", "127.0.0.172", "127.0.0.10");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug);

        assertEquals(2, result.size(), "Should return two options (121 and 249)");
        assertEquals("aggregate_opt_121 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a", result.get(0), "Option 121 should match");
        assertEquals("aggregate_opt_249 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a", result.get(1), "Option 249 should match");
    }

    @Test
    public void testGenerateDhcpOptions_InvalidNetworkFormat() {
        List<String> networks = Collections.singletonList("192.168.0.0");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        // Перенаправляємо System.err для перевірки повідомлення про помилку
        java.io.ByteArrayOutputStream errContent = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errContent));

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug);

        System.setErr(System.err); // Відновлюємо System.err

        assertTrue(result.isEmpty(), "Should return empty list for invalid network format");
        assertTrue(errContent.toString().contains("Invalid network format: 192.168.0.0"), "Should print error message");
    }

    /*
    Тести для parseDhcpOptions:
        testParseDhcpOptions_ValidHexWithPrefix: Перевіряє декодування hex-рядка з 0x.
        testParseDhcpOptions_ValidHexWithoutPrefix: Перевіряє декодування без 0x.
        testParseDhcpOptions_MultipleRoutes: Перевіряє декодування кількох маршрутів.
        testParseDhcpOptions_InvalidHexFormat: Перевіряє обробку невалідного hex.
        testParseDhcpOptions_NullInput: Перевіряє обробку null.
     */
    @Test
    public void testParseDhcpOptions_ValidHexWithPrefix() {
        String hexOption = "0x10c0a87f0000c0";

        List<String> result = DhcpRouteConverter.parseDhcpOptions(hexOption);

        assertEquals(1, result.size(), "Should return one route");
        assertEquals("192.168.0.0/16 via 127.0.0.192", result.get(0), "Route should match");
    }

    @Test
    public void testParseDhcpOptions_ValidHexWithoutPrefix() {
        String hexOption = "10c0a87f0000c0";

        List<String> result = DhcpRouteConverter.parseDhcpOptions(hexOption);

        assertEquals(1, result.size(), "Should return one route");
        assertEquals("192.168.0.0/16 via 127.0.0.192", result.get(0), "Route should match");
    }

    @Test
    public void testParseDhcpOptions_MultipleRoutes() {
        String hexOption = "10c0a87f0000c00cac107f0000ac080a7f00000a";

        List<String> result = DhcpRouteConverter.parseDhcpOptions(hexOption);

        assertEquals(3, result.size(), "Should return three routes");
        assertEquals("192.168.0.0/16 via 127.0.0.192", result.get(0), "First route should match");
        assertEquals("172.16.0.0/12 via 127.0.0.172", result.get(1), "Second route should match");
        assertEquals("10.0.0.0/8 via 127.0.0.10", result.get(2), "Third route should match");
    }

    @Test
    public void testParseDhcpOptions_InvalidHexFormat() {
        String hexOption = "invalid_hex";

        // Перенаправляємо System.err для перевірки повідомлення про помилку
        java.io.ByteArrayOutputStream errContent = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errContent));

        List<String> result = DhcpRouteConverter.parseDhcpOptions(hexOption);

        System.setErr(System.err); // Відновлюємо System.err

        assertTrue(result.isEmpty(), "Should return empty list for invalid hex");
        assertTrue(errContent.toString().contains("Invalid hex option format"), "Should print error message");
    }

    @Test
    public void testParseDhcpOptions_NullInput() {
        // Перенаправляємо System.err для перевірки повідомлення про помилку
        java.io.ByteArrayOutputStream errContent = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errContent));

        List<String> result = DhcpRouteConverter.parseDhcpOptions(null);

        System.setErr(System.err); // Відновлюємо System.err

        assertTrue(result.isEmpty(), "Should return empty list for null input");
        assertTrue(errContent.toString().contains("Invalid hex option format"), "Should print error message");
    }
}

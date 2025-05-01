package net.ukrcom.dhcprouteconverter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DhcpRouteConverterTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    public void setUp() {
        // Перенаправляємо System.out і System.err перед кожним тестом
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
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

    /*
    Тести для toHex:
        testToHex_Zero: Перевіряє, чи 0 конвертується в "00".
        testToHex_SingleDigit: Перевіряє, чи 5 конвертується в "05".
        testToHex_MaxByte: Перевіряє, чи 255 конвертується в "ff".
        testToHex_MidRange: Перевіряє, чи 192 конвертується в "c0".
     */
    @Test
    public void testToHex_Zero() {
        String result = DhcpRouteConverter.toHex(0);
        assertEquals("00", result, "Zero should convert to '00'");
    }

    @Test
    public void testToHex_SingleDigit() {
        String result = DhcpRouteConverter.toHex(5);
        assertEquals("05", result, "Single digit 5 should convert to '05'");
    }

    @Test
    public void testToHex_MaxByte() {
        String result = DhcpRouteConverter.toHex(255);
        assertEquals("ff", result, "255 should convert to 'ff'");
    }

    @Test
    public void testToHex_MidRange() {
        String result = DhcpRouteConverter.toHex(192);
        assertEquals("c0", result, "192 should convert to 'c0'");
    }

    /*
    Тести для main:
        testMain_NoArguments: Перевіряє, чи викликається printHelp при відсутності аргументів.
        testMain_HelpOption: Перевіряє, чи --help виводить довідку.
        testMain_InvalidOption: Перевіряє, чи невалідна опція (наприклад, --invalid) видає помилку.
        testMain_TdoMissingArguments: Перевіряє, чи -tdo без аргументів видає помилку.
        testMain_FdoMissingArguments: Перевіряє, чи -fdo без аргументів видає помилку.
     */
    @Test
    public void testMain_NoArguments() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{});

        String output = outContent.toString();
        assertTrue(output.contains("DhcpRouteConverter - A utility to convert between network routes and DHCP options 121/249."),
                "Should print help message when no arguments provided");
        assertTrue(output.contains("Usage:"), "Help message should include usage");
    }

    @Test
    public void testMain_HelpOption() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"--help"});

        String output = outContent.toString();
        assertTrue(output.contains("DhcpRouteConverter - A utility to convert between network routes and DHCP options 121/249."),
                "Should print help message for --help");
        assertTrue(output.contains("Options:"), "Help message should include options");
    }

    @Test
    public void testMain_InvalidOption() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"--invalid"});

        String error = errContent.toString();
        assertTrue(error.contains("Error: Unknown option. Use --help for usage information."),
                "Should print error for invalid option");
    }

    @Test
    public void testMain_TdoMissingArguments() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"-tdo"});

        String error = errContent.toString();
        assertTrue(error.contains("Error: Missing arguments for -tdo. Use --help for usage information."),
                "Should print error for -tdo without arguments");
    }

    @Test
    public void testMain_FdoMissingArguments() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"-fdo"});

        String error = errContent.toString();
        assertTrue(error.contains("Error: Missing arguments for -fdo. Use --help for usage information."),
                "Should print error for -fdo without arguments");
    }
}

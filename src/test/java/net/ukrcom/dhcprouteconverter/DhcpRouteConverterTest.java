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
        outContent.reset();
        errContent.reset();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @Test
    public void testGenerateDhcpOptions_ValidSingleRoute() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.DEFAULT, "lan-pool", "mypool");

        assertEquals(2, result.size(), "Should return two options (121 and 249)");
        assertEquals("aggregate_opt_121 : 0x10c0a87f0000c0", result.get(0), "Option 121 should match");
        assertEquals("aggregate_opt_249 : 0x10c0a87f0000c0", result.get(1), "Option 249 should match");
    }

    @Test
    public void testGenerateDhcpOptions_MultipleRoutes() {
        List<String> networks = Arrays.asList("192.168.0.0/16", "172.16.0.0/12", "10.0.0.0/8");
        List<String> gateways = Arrays.asList("127.0.0.192", "127.0.0.172", "127.0.0.10");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.DEFAULT, "lan-pool", "mypool");

        assertEquals(2, result.size(), "Should return two options (121 and 249)");
        assertEquals("aggregate_opt_121 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a", result.get(0), "Option 121 should match");
        assertEquals("aggregate_opt_249 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a", result.get(1), "Option 249 should match");
    }

    @Test
    public void testGenerateDhcpOptions_InvalidNetworkFormat() {
        List<String> networks = Collections.singletonList("192.168.0.0");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.DEFAULT, "lan-pool", "mypool");

        assertTrue(result.isEmpty(), "Should return empty list for invalid network format");
        assertTrue(errContent.toString().contains("Invalid network format: 192.168.0.0"), "Should print error message");
    }

    @Test
    public void testGenerateDhcpOptions_DebugOutput() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = true;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.DEFAULT, "lan-pool", "mypool");

        String output = outContent.toString();
        assertEquals(2, result.size(), "Should return two options (121 and 249)");
        assertTrue(output.contains("option_121_route_192.168.0.0/16_via_127.0.0.192 : 0x10c0a87f0000c0"),
                "Should print debug output for option 121");
        assertTrue(output.contains("option_249_route_192.168.0.0/16_via_127.0.0.192 : 0x10c0a87f0000c0"),
                "Should print debug output for option 249");
    }

    @Test
    public void testGenerateDhcpOptions_IscFormat() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.ISC, "lan-pool", "mypool");

        assertEquals(4, result.size(), "Should return four lines for isc format");
        assertEquals("option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;", result.get(0));
        assertEquals("option ms-classless-static-routes code 249 = array of unsigned integer 8;", result.get(1));
        assertEquals("option rfc3442-classless-static-routes 16,192,168,127,0,0,192;", result.get(2));
        assertEquals("option ms-classless-static-routes 16,192,168,127,0,0,192;", result.get(3));
    }

    @Test
    public void testGenerateDhcpOptions_RouterOsFormat() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.ROUTEROS, "lan-pool", "mypool");

        assertEquals(2, result.size(), "Should return two lines for routeros format");
        assertEquals("/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x10c0a87f0000c0", result.get(0));
        assertEquals("/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x10c0a87f0000c0", result.get(1));
    }

    @Test
    public void testGenerateDhcpOptions_JunosFormat() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.JUNOS, "vlan100-pool", "mypool");

        assertEquals(2, result.size(), "Should return two lines for junos format");
        assertEquals("set access address-assignment pool vlan100-pool family inet dhcp-attributes option 121 hex-string 10c0a87f0000c0", result.get(0));
        assertEquals("set access address-assignment pool vlan100-pool family inet dhcp-attributes option 249 hex-string 10c0a87f0000c0", result.get(1));
    }

    @Test
    public void testGenerateDhcpOptions_CiscoFormat_DefaultPool() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.CISCO, "lan-pool", "mypool");

        assertEquals(3, result.size(), "Should return three lines for cisco format");
        assertEquals("ip dhcp pool mypool", result.get(0));
        assertEquals(" option 121 hex 10c0a87f0000c0", result.get(1));
        assertEquals(" option 249 hex 10c0a87f0000c0", result.get(2));
    }

    @Test
    public void testGenerateDhcpOptions_CiscoFormat_CustomPool() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.CISCO, "lan-pool", "custom-pool");

        assertEquals(3, result.size(), "Should return three lines for cisco format");
        assertEquals("ip dhcp pool custom-pool", result.get(0));
        assertEquals(" option 121 hex 10c0a87f0000c0", result.get(1));
        assertEquals(" option 249 hex 10c0a87f0000c0", result.get(2));
    }

    @Test
    public void testGenerateDhcpOptions_WindowsFormat() {
        List<String> networks = Collections.singletonList("192.168.0.0/16");
        List<String> gateways = Collections.singletonList("127.0.0.192");
        boolean debug = false;

        List<String> result = DhcpRouteConverter.generateDhcpOptions(networks, gateways, debug, DhcpRouteConverter.Format.WINDOWS, "lan-pool", "mypool");

        assertEquals(2, result.size(), "Should return two lines for windows format");
        assertEquals("Set-DhcpServerv4OptionValue -OptionId 121 -Value 0x10c0a87f0000c0", result.get(0));
        assertEquals("Set-DhcpServerv4OptionValue -OptionId 249 -Value 0x10c0a87f0000c0", result.get(1));
    }

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

        List<String> result = DhcpRouteConverter.parseDhcpOptions(hexOption);

        assertTrue(result.isEmpty(), "Should return empty list for invalid hex");
        assertTrue(errContent.toString().contains("Invalid hex option format"), "Should print error message");
    }

    @Test
    public void testParseDhcpOptions_NullInput() {
        List<String> result = DhcpRouteConverter.parseDhcpOptions(null);

        assertTrue(result.isEmpty(), "Should return empty list for null input");
        assertTrue(errContent.toString().contains("Invalid hex option format"), "Should print error message");
    }

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

    @Test
    public void testMain_MutuallyExclusiveFormats() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"-tdo", "--isc", "--cisco", "192.168.0.0/16,127.0.0.192"});

        String error = errContent.toString();
        assertTrue(error.contains("Error: Only one format can be specified (--isc, --routeros, --junos, --cisco, --windows)."),
                "Should print error for multiple format arguments");
    }

    @Test
    public void testMain_JunosCustomPool() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"-tdo", "--junos=vlan100-pool", "192.168.0.0/16,127.0.0.192"});

        String output = outContent.toString();
        assertTrue(output.contains("set access address-assignment pool vlan100-pool family inet dhcp-attributes option 121 hex-string 10c0a87f0000c0"),
                "Should use custom JunOS pool name");
        assertTrue(output.contains("set access address-assignment pool vlan100-pool family inet dhcp-attributes option 249 hex-string 10c0a87f0000c0"),
                "Should use custom JunOS pool name for option 249");
    }

    @Test
    public void testMain_CiscoDefaultPool() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"-tdo", "--cisco", "192.168.0.0/16,127.0.0.192"});

        String output = outContent.toString();
        assertTrue(output.contains("ip dhcp pool mypool"),
                "Should use default Cisco pool name");
        assertTrue(output.contains(" option 121 hex 10c0a87f0000c0"),
                "Should include option 121");
        assertTrue(output.contains(" option 249 hex 10c0a87f0000c0"),
                "Should include option 249");
    }

    @Test
    public void testMain_CiscoCustomPool() {
        outContent.reset();
        errContent.reset();
        DhcpRouteConverter.main(new String[]{"-tdo", "--cisco=custom-pool", "192.168.0.0/16,127.0.0.192"});

        String output = outContent.toString();
        assertTrue(output.contains("ip dhcp pool custom-pool"),
                "Should use custom Cisco pool name");
        assertTrue(output.contains(" option 121 hex 10c0a87f0000c0"),
                "Should include option 121");
        assertTrue(output.contains(" option 249 hex 10c0a87f0000c0"),
                "Should include option 249");
    }
}
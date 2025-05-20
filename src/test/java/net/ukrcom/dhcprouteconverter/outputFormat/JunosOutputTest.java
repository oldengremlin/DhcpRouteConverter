package net.ukrcom.dhcprouteconverter.outputFormat;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JunosOutputTest {

    @Test
    void testJunosFormatDhcpOptions() {
        JUNOS junos = new JUNOS("18c0a8010a000001", true, "r540pool1");
        List<String> options = junos.formatDhcpOptions();
        assertEquals(2, options.size());
        assertTrue(options.get(0).contains("option 121 hex-string 18c0a8010a000001"));
        assertTrue(options.get(1).contains("option 249 hex-string 18c0a8010a000001"));
    }

    @Test
    void testJunosEmptyHex() {
        JUNOS junos = new JUNOS("", false, "r540pool1");
        List<String> options = junos.formatDhcpOptions();
        assertTrue(options.isEmpty());
    }
}

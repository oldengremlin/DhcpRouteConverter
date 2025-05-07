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

import java.util.ArrayList;
import java.util.List;

import net.ukrcom.dhcprouteconverter.DhcpOptionConverter.Format;
import net.ukrcom.dhcprouteconverter.outputFormat.*;

/**
 * Formats DHCP option strings for various DHCP server configurations.
 */
public class OutputFormatter {

    /**
     * Formats DHCP options for the specified format.
     *
     * @param aggregateHex Hexadecimal string of DHCP options.
     * @param withOption249 If true, includes option 249 (Microsoft-specific).
     * @param format Output format (e.g., ISC, JUNOS, CISCO).
     * @param junosPoolName Pool name for JunOS format.
     * @param ciscoPoolName Pool name for Cisco format.
     * @return List of formatted DHCP option strings.
     */
    public List<String> formatDhcpOptions(String aggregateHex, boolean withOption249, Format format, String junosPoolName, String ciscoPoolName) {
        List<String> results = new ArrayList<>();
        if (aggregateHex.isEmpty()) {
            return results;
        }

        net.ukrcom.dhcprouteconverter.outputFormat.outputFormatInterface of = null;

        switch (format) {
            case DEFAULT -> {
                of = new DEFAULT(aggregateHex, withOption249, null);
            }
            case ISC -> {
                of = new ISC(aggregateHex, withOption249, null);
            }
            case ROUTEROS -> {
                of = new ROUTEROS(aggregateHex, withOption249, null);
            }
            case JUNOS -> {
                of = new JUNOS(aggregateHex, withOption249, junosPoolName);
            }
            case CISCO -> {
                of = new CISCO(aggregateHex, withOption249, ciscoPoolName);
            }
            case WINDOWS -> {
                of = new WINDOWS(aggregateHex, withOption249, null);
            }
            default ->
                throw new AssertionError("Unknown format: " + format);
        }

        if (of != null) {
            results = of.formatDhcpOptions();
        }

        return results;
    }

    /**
     * Formats a list of DHCP options into a single string.
     *
     * @param dhcpOptions List of DHCP option strings.
     * @return Formatted string with options separated by newlines.
     */
    public String format(List<String> dhcpOptions) {
        StringBuilder output = new StringBuilder();
        for (String option : dhcpOptions) {
            output.append(option).append("\n");
        }
        return output.toString();
    }

    /**
     * Get configuration from devices.
     *
     * @param config Configuration string.
     * @param username Username for authentication.
     * @param password Password for authentication.
     * @param format Output format (e.g., ISC, JUNOS, CISCO).
     * @param method Application method.
     */
    public void getConfig(String config, String username, String password, Format format, ApplyMethod method) {

        net.ukrcom.dhcprouteconverter.outputFormat.outputFormatInterface of = null;

        switch (format) {
            case DEFAULT -> {
                of = new DEFAULT(config, username, password, method);
            }
            case ISC -> {
                of = new ISC(config, username, password, method);
            }
            case ROUTEROS -> {
                of = new ROUTEROS(config, username, password, method);
            }
            case JUNOS -> {
                of = new JUNOS(config, username, password, method);
            }
            case CISCO -> {
                of = new CISCO(config, username, password, method);
            }
            case WINDOWS -> {
                of = new WINDOWS(config, username, password, method);
            }
            default ->
                throw new AssertionError("Unknown format: " + format);
        }

        if (of != null) {
            List<String> currentConfig = of.getConfig();
        }
    }

    /**
     * Applies the configuration to a devices.
     *
     * @param config Configuration string.
     * @param username Username for authentication.
     * @param password Password for authentication.
     * @param format Output format (e.g., ISC, JUNOS, CISCO).
     * @param method Application method.
     */
    public void applyConfig(String config, String username, String password, Format format, ApplyMethod method) {

        net.ukrcom.dhcprouteconverter.outputFormat.outputFormatInterface of = null;

        switch (format) {
            case DEFAULT -> {
                of = new DEFAULT(config, username, password, method);
            }
            case ISC -> {
                of = new ISC(config, username, password, method);
            }
            case ROUTEROS -> {
                of = new ROUTEROS(config, username, password, method);
            }
            case JUNOS -> {
                of = new JUNOS(config, username, password, method);
            }
            case CISCO -> {
                of = new CISCO(config, username, password, method);
            }
            case WINDOWS -> {
                of = new WINDOWS(config, username, password, method);
            }
            default ->
                throw new AssertionError("Unknown format: " + format);
        }

        if (of != null) {
            of.applyConfig();
        }
    }
}

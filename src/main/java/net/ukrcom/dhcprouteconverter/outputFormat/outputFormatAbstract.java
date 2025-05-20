/*
 * Copyright 2025 ukr-com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ukrcom.dhcprouteconverter.outputFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.ukrcom.dhcprouteconverter.ApplyMethod;
import net.ukrcom.dhcprouteconverter.ArgumentParser;
import net.ukrcom.dhcprouteconverter.PoolDeviceConfig;
import net.ukrcom.dhcprouteconverter.RouterDeviceConfig;

/**
 *
 * @author olden
 */
public abstract class outputFormatAbstract implements outputFormatInterface {

    // Variables for formatDhcpOptions()
    protected String aggregateHex;
    protected boolean withOption249;
    protected String poolName;

    // Variables for applyConfig()
    protected String config;
    protected String username;
    protected String password;
    protected ApplyMethod method;

    protected ArgumentParser globalOptions;

    /**
     * Constructor for formatDhcpOptions
     *
     * @param aggregateHex Hexadecimal string of DHCP options.
     * @param withOption249 If true, includes option 249 (Microsoft-specific).
     * @param poolName Pool name for JunOS format.
     */
    outputFormatAbstract(String aggregateHex, boolean withOption249, String poolName) {
        this.aggregateHex = aggregateHex;
        this.withOption249 = withOption249;
        this.poolName = poolName;
    }

    /**
     * Constructor for applyConfig()
     *
     * @param config Configuration string.
     * @param username Username for authentication.
     * @param password Password for authentication.
     * @param method Application method.
     */
    outputFormatAbstract(String config, String username, String password, ApplyMethod method, ArgumentParser parser) {
        this.config = config;
        this.username = username;
        this.password = password;
        this.method = method;
        this.globalOptions = parser;
    }

    /**
     * Formats DHCP options for the specified format.
     *
     * @return List of formatted DHCP option strings.
     */
    @Override
    public List<String> formatDhcpOptions() {
        List<String> results = new ArrayList<>();
        if (aggregateHex.isEmpty()) {
            return results;
        }
        return results;
    }

    /**
     * Get configuration of pool's from devices.
     *
     * @param routerName
     * @param deviceConfig
     * @return
     */
    @Override
    public Map<String, PoolDeviceConfig> getConfig(String routerName, RouterDeviceConfig deviceConfig) {
        System.err.println("[outputFormatAbstract.getAllPools] Not implemented for " + routerName);
        return new HashMap<>();
    }

    /**
     * Applies the configuration to a devices.
     */
    @Override
    public void applyConfig() {
        System.err.println("[outputFormatAbstract.apply] " + config + ", " + username + ", " + password + ", " + method);
    }
}

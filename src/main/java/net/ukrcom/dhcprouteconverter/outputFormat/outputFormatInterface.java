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

import java.util.List;
import java.util.Map;
import net.ukrcom.dhcprouteconverter.PoolDeviceConfig;
import net.ukrcom.dhcprouteconverter.RouterDeviceConfig;

/**
 *
 * @author olden
 */
public interface outputFormatInterface {

    /**
     * Formats DHCP options for the specified format.
     *
     * @return List of formatted DHCP option strings.
     */
    public List<String> formatDhcpOptions();

    /**
     * Get configuration of pool's from devices.
     *
     * @param routerName
     * @param deviceConfig
     * @return
     */
    public Map<String, PoolDeviceConfig> getConfig(String routerName, RouterDeviceConfig deviceConfig);

    /**
     * Applies the configuration to a devices.
     */
    public void applyConfig();

}

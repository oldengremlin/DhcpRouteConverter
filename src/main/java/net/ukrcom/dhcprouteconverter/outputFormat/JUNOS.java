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
import java.util.List;
import net.ukrcom.dhcprouteconverter.ApplyMethod;

/**
 *
 * @author olden
 */
public class JUNOS extends outputFormatAbstract implements outputFormatInterface {

    public JUNOS(String aggregateHex, boolean withOption249, String poolName) {
        super(aggregateHex, withOption249, poolName);
    }

    public JUNOS(String config, String username, String password, ApplyMethod method) {
        super(config, username, password, method);
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

        results.add(String.format(
                "set access address-assignment pool %s family inet dhcp-attributes option 121 hex-string %s",
                poolName,
                aggregateHex
        ));
        if (withOption249) {
            results.add(String.format(
                    "set access address-assignment pool %s family inet dhcp-attributes option 249 hex-string %s",
                    poolName,
                    aggregateHex
            ));
        }

        return results;
    }

    private String buildNetconfGetRequest(String poolName) {
        return "<rpc>"
                + "<get-configuration>"
                + " <configuration>"
                + "  <access>"
                + "   <address-assignment>"
                + "    <pool>"
                + "     <name>" + poolName + "</name>"
                + "    </pool>"
                + "   </address-assignment>"
                + "  </access>"
                + " </configuration>"
                + "</get-configuration>"
                + "</rpc>";
    }

    private String buildNetconfEditRequest(String poolName, String hexValue) {
        return "<rpc>"
                + "<edit-configuration>"
                + " <target>"
                + "  <candidate/>"
                + " </target>"
                + " <config>"
                + "  <access>"
                + "   <address-assignment>"
                + "    <pool>"
                + "     <name>" + poolName + "</name>"
                + "     <family>"
                + "      <inet>"
                + "       <dhcp-attributes>"
                + "        <option>"
                + "         <name>121</name>"
                + "         <value>0x" + hexValue + "</value>"
                + "        </option>"
                + "       </dhcp-attributes>"
                + "      </inet>"
                + "     </family>"
                + "    </pool>"
                + "   </address-assignment>"
                + "  </access>"
                + " </config>"
                + "</edit-configuration>"
                + "</rpc>";
    }
}

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
import net.ukrcom.dhcprouteconverter.ArgumentParser;

/**
 *
 * @author olden
 */
public class ROUTEROS extends outputFormatAbstract implements outputFormatInterface {

    public ROUTEROS(String aggregateHex, boolean withOption249, String poolName) {
        super(aggregateHex, withOption249, poolName);
    }

    public ROUTEROS(String config, String username, String password, ApplyMethod method, ArgumentParser parser) {
        super(config, username, password, method, parser);
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
                "/ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x%s",
                aggregateHex
        ));
        if (withOption249) {
            results.add(String.format(
                    "/ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x%s",
                    aggregateHex
            ));
        }

        return results;
    }
}

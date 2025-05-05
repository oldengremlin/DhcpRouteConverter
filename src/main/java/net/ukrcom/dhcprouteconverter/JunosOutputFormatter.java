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

import java.util.List;

public class JunosOutputFormatter implements OutputFormatter {

    @Override
    public String format(List<String> dhcpOptions) {
        StringBuilder output = new StringBuilder();
        for (String option : dhcpOptions) {
            output.append(option).append("\n");
        }
        return output.toString();
    }

    @Override
    public void apply(String config, String username, String password, ApplyMethod method) {
        // Заглушка для NETCONF (реалізується в v3.0.0)
        System.err.println("DEBUG: Applying config via " + method + " (username: " + username + ") - not implemented yet");
    }
}

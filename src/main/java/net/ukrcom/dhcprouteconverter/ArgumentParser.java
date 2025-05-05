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

public class ArgumentParser {

    private String configFile;
    private String addDefaultMultiPool;
    private String addDefaultGateway;
    private String commonRoutes;
    private boolean withoutWarnNoDefaultRoute;
    private boolean withWarningLoopback;
    private String toDhcpOptions;
    private String fromDhcpOptions;
    private boolean debug;
    private String format;
    private String junosPoolName;
    private String ciscoPoolName;
    private boolean withOption249;

    public ArgumentParser() {
        this.junosPoolName = "lan-pool";
        this.ciscoPoolName = "mypool";
        this.format = "default";
        this.withOption249 = false;
    }

    public void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--config=")) {
                configFile = arg.substring("--config=".length());
            } else if (arg.startsWith("--add-default-multi-pool=")) {
                addDefaultMultiPool = arg.substring("--add-default-multi-pool=".length());
            } else if (arg.startsWith("--add-default-gateway=")) {
                addDefaultGateway = arg.substring("--add-default-gateway=".length());
            } else if (arg.startsWith("--common-routes=")) {
                commonRoutes = arg.substring("--common-routes=".length());
            } else if (arg.equals("--without-warn-no-default-route")) {
                withoutWarnNoDefaultRoute = true;
            } else if (arg.equals("--with-warning-loopback")) {
                withWarningLoopback = true;
            } else if (arg.equals("--with-option-249")) {
                withOption249 = true;
            } else if ((arg.equals("--to-dhcp-options") || arg.equals("-tdo"))) {
                int argIndex = i + 1;
                if (argIndex < args.length && args[argIndex].equals("-d")) {
                    debug = true;
                    argIndex++;
                }
                if (argIndex < args.length) {
                    if (args[argIndex].equals("--isc")) {
                        format = "isc";
                        argIndex++;
                    } else if (args[argIndex].startsWith("--routeros=")) {
                        format = "routeros";
                        argIndex++;
                    } else if (args[argIndex].equals("--routeros")) {
                        format = "routeros";
                        argIndex++;
                    } else if (args[argIndex].equals("--junos")) {
                        format = "junos";
                        argIndex++;
                    } else if (args[argIndex].startsWith("--junos=")) {
                        format = "junos";
                        junosPoolName = args[argIndex].substring("--junos=".length());
                        argIndex++;
                    } else if (args[argIndex].equals("--cisco")) {
                        format = "cisco";
                        argIndex++;
                    } else if (args[argIndex].startsWith("--cisco=")) {
                        format = "cisco";
                        ciscoPoolName = args[argIndex].substring("--cisco=".length());
                        argIndex++;
                    } else if (args[argIndex].equals("--windows")) {
                        format = "windows";
                        argIndex++;
                    }
                }
                if (argIndex < args.length && !args[argIndex].startsWith("-")) {
                    toDhcpOptions = args[argIndex];
                    i = argIndex;
                }
            } else if ((arg.equals("--from-dhcp-options") || arg.equals("-fdo")) && i + 1 < args.length) {
                fromDhcpOptions = args[++i];
            }
        }
    }

    public String getConfigFile() {
        return configFile;
    }

    public String getAddDefaultMultiPool() {
        return addDefaultMultiPool;
    }

    public String getAddDefaultGateway() {
        return addDefaultGateway;
    }

    public String getCommonRoutes() {
        return commonRoutes;
    }

    public boolean isWithoutWarnNoDefaultRoute() {
        return withoutWarnNoDefaultRoute;
    }

    public boolean isWithWarningLoopback() {
        return withWarningLoopback;
    }

    public String getToDhcpOptions() {
        return toDhcpOptions;
    }

    public String getFromDhcpOptions() {
        return fromDhcpOptions;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getFormat() {
        return format;
    }

    public String getJunosPoolName() {
        return junosPoolName;
    }

    public String getCiscoPoolName() {
        return ciscoPoolName;
    }

    public boolean isWithOption249() {
        return withOption249;
    }
}
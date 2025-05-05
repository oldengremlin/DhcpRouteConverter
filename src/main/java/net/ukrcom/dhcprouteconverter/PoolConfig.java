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
import java.util.Map;

public class PoolConfig {

    private String defaultGateway;
    private List<Map<String, String>> commonRoutes;
    private boolean disableAppendRoutes;

    public PoolConfig() {
        this.commonRoutes = new ArrayList<>();
        this.disableAppendRoutes = false;
    }

    public static PoolConfig fromMap(Map<String, Object> map) {
        PoolConfig config = new PoolConfig();
        config.defaultGateway = (String) map.get("default-gateway");
        config.disableAppendRoutes = (Boolean) map.getOrDefault("disable-append-routes", false);
        List<Map<String, String>> commonRoutes = (List<Map<String, String>>) map.get("common-routes");
        if (commonRoutes != null) {
            config.commonRoutes = commonRoutes;
        }
        return config;
    }

    public String getDefaultGateway() {
        return defaultGateway;
    }

    public void setDefaultGateway(String defaultGateway) {
        this.defaultGateway = defaultGateway;
    }

    public List<Map<String, String>> getCommonRoutes() {
        return commonRoutes;
    }

    public void setCommonRoutes(List<Map<String, String>> commonRoutes) {
        this.commonRoutes = commonRoutes;
    }

    public boolean isDisableAppendRoutes() {
        return disableAppendRoutes;
    }

    public void setDisableAppendRoutes(boolean disableAppendRoutes) {
        this.disableAppendRoutes = disableAppendRoutes;
    }
}

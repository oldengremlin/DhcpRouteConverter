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

import java.util.HashMap;
import java.util.Map;

public class RouterConfig {

    private String name;
    private String username;
    private String password;
    private ApplyMethod applyMethod;
    private boolean disableAppendRoutes;
    private Map<String, PoolConfig> pools;

    public RouterConfig() {
        this.pools = new HashMap<>();
        this.disableAppendRoutes = false;
    }

    public static RouterConfig fromMap(Map<String, Object> map) {
        RouterConfig config = new RouterConfig();
        config.name = (String) map.get("name");
        config.disableAppendRoutes = (Boolean) map.getOrDefault("disable-append-routes", false);
        Map<String, Object> poolsMap = (Map<String, Object>) map.get("pools");
        if (poolsMap != null) {
            for (Map.Entry<String, Object> entry : poolsMap.entrySet()) {
                config.pools.put(entry.getKey(), PoolConfig.fromMap((Map<String, Object>) entry.getValue()));
            }
        }
        return config;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ApplyMethod getApplyMethod() {
        return applyMethod;
    }

    public void setApplyMethod(ApplyMethod applyMethod) {
        this.applyMethod = applyMethod;
    }

    public boolean isDisableAppendRoutes() {
        return disableAppendRoutes;
    }

    public void setDisableAppendRoutes(boolean disableAppendRoutes) {
        this.disableAppendRoutes = disableAppendRoutes;
    }

    public Map<String, PoolConfig> getPools() {
        return pools;
    }

    public void setPools(Map<String, PoolConfig> pools) {
        this.pools = pools;
    }
}

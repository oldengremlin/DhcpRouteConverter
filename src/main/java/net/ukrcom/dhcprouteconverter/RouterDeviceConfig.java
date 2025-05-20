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

public class RouterDeviceConfig {

    private final String name;
    private final String username;
    private final String password;
    private final ApplyMethod applyMethod;
    private final Map<String, PoolDeviceConfig> pools;

    public RouterDeviceConfig(String name, String username, String password, ApplyMethod applyMethod) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.applyMethod = applyMethod;
        this.pools = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public ApplyMethod getApplyMethod() {
        return applyMethod;
    }

    public Map<String, PoolDeviceConfig> getPools() {
        return pools;
    }

    public void addPool(String poolName, PoolDeviceConfig poolConfig) {
        pools.put(poolName, poolConfig);
    }
}

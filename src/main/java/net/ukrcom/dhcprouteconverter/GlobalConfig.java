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

public class GlobalConfig {

    private String username;
    private String password;
    private ApplyMethod applyMethod;
    private List<Map<String, String>> appendRoutes;
    private List<Map<String, String>> commonRoutes;

    public GlobalConfig() {
        this.appendRoutes = new ArrayList<>();
        this.commonRoutes = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static GlobalConfig fromMap(Map<String, Object> map) {
        GlobalConfig config = new GlobalConfig();
        config.username = (String) map.get("username");
        config.password = (String) map.get("password");
        String applyMethodStr = (String) map.get("apply-method");
        if (applyMethodStr != null) {
            config.applyMethod = ApplyMethod.valueOf(applyMethodStr.toUpperCase());
        }
        List<Map<String, String>> appendRoutes = (List<Map<String, String>>) map.get("append-routes");
        if (appendRoutes != null) {
            config.appendRoutes = appendRoutes;
        }
        return config;
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

    public List<Map<String, String>> getAppendRoutes() {
        return appendRoutes;
    }

    public void setAppendRoutes(List<Map<String, String>> appendRoutes) {
        this.appendRoutes = appendRoutes;
    }

    public List<Map<String, String>> getCommonRoutes() {
        return commonRoutes;
    }

    public void setCommonRoutes(List<Map<String, String>> commonRoutes) {
        this.commonRoutes = commonRoutes;
    }
}

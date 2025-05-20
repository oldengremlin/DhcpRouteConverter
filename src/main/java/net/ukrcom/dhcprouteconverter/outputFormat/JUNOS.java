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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.ukrcom.dhcprouteconverter.ApplyMethod;
import net.ukrcom.dhcprouteconverter.PoolDeviceConfig;
import net.ukrcom.dhcprouteconverter.RouterDeviceConfig;
import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.XML;
import net.ukrcom.dhcprouteconverter.ArgumentParser;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

/**
 *
 * @author olden
 */
public class JUNOS extends outputFormatAbstract implements outputFormatInterface {

    public JUNOS(String aggregateHex, boolean withOption249, String poolName) {
        super(aggregateHex, withOption249, poolName);
    }

    public JUNOS(String config, String username, String password, ApplyMethod method, ArgumentParser parser) {
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

    @Override
    public void applyConfig() {
        if (method != ApplyMethod.NETCONF) {
            System.err.println("ERROR: NETCONF method required for applying config");
            return;
        }
        /*
        Device device = null;
        try {
            // Налаштування підключення до роутера
            device = new Device(config + ".ukrhub.net", username, password, null, 830);
            device.connect();

            // Формуємо запит для редагування конфігурації
            String editRequest = buildNetconfEditRequest(poolName, aggregateHex);
            XML response = device.executeRPC(editRequest);

            // Перевіряємо відповідь
            if (response.toString().contains("<ok/>")) {
                System.out.println("Successfully applied config to pool: " + poolName);
                // Коміт конфігурації
                device.commit();
            } else {
                System.err.println("ERROR: Failed to apply config: " + response.toString());
            }

        } catch (NetconfException e) {
            System.err.println("ERROR: Failed to apply config to router " + config + ": " + e.getMessage());
        } finally {
            if (device != null) {
                device.close();
            }
        }
         */
    }

    @Override
    public Map<String, PoolDeviceConfig> getConfig(String routerName, RouterDeviceConfig deviceConfig) {
        if (method != ApplyMethod.NETCONF) {
            System.err.println("ERROR: NETCONF method required for router " + routerName);
            return new HashMap<>();
        }

        Map<String, PoolDeviceConfig> pools = new HashMap<>();
        Device device = null;

        try {

            String hostname = routerName + ".ukrhub.net";

            if (globalOptions.isDebug()) {
                System.err.println("Starting NETCONF connection to router: "
                        + routerName
                        + " (" + hostname
                        + " [" + deviceConfig.getUsername() + ", " + deviceConfig.getPassword() + "]"
                        + ")");
            }

            // Вимкнути логи JSch
            JSch.setLogger(new com.jcraft.jsch.Logger() {
                @Override
                public boolean isEnabled(int level) {
                    return globalOptions.isDebug(); // Вимкнути всі логи JSch
                }

                @Override
                public void log(int level, String message) {
                    System.err.println("JSch [" + level + "]: " + message);
                }
            });

            java.util.Properties sshConfig = new java.util.Properties();

            sshConfig.put("StrictHostKeyChecking", "no");
            sshConfig.put("PreferredAuthentications", "password");

            sshConfig.put("kex", "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256");
            sshConfig.put("server_host_key", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256");
            sshConfig.put("cipher.s2c", "aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr");
            sshConfig.put("cipher.c2s", "aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr");
            sshConfig.put("mac.s2c", "hmac-sha2-256,hmac-sha2-512");
            sshConfig.put("mac.c2s", "hmac-sha2-256,hmac-sha2-512");

            JSch jsch = new JSch();
            com.jcraft.jsch.Session session;
            session = jsch.getSession(deviceConfig.getUsername(), hostname, 830);
            session.setPassword(deviceConfig.getPassword());
            session.setConfig(sshConfig);

            // Налаштування підключення до роутера
            device = net.juniper.netconf.Device.builder()
                    .hostName(hostname)
                    .userName(deviceConfig.getUsername())
                    .password(deviceConfig.getPassword())
                    .strictHostKeyChecking(false)
                    .sshClient(jsch)
                    .build();
            if (globalOptions.isDebug()) {
                System.err.println("Device: " + device.toString());
            }

            device.connect();
            if (globalOptions.isDebug()) {
                System.err.println("Device isConnected: " + device.isConnected());
            }

            // Виконуємо запит для отримання конфігурації
            String getRequest = buildNetconfGetAllPoolsRequest();
            XML response = device.executeRPC(getRequest);

            // Парсимо відповідь
            String responseXml = response.toString();
            pools.putAll(parseNetconfResponse(responseXml, routerName));

        } catch (NetconfException e) {
            System.err.println("ERROR: Failed to connect or execute NETCONF RPC on router " + routerName + ": " + e.getMessage());
        } catch (JSchException e) {
            System.err.println("ERROR: Failed to connect to router " + routerName + ": " + e.getMessage());
        } catch (SAXException | IOException ex) {
            Logger.getLogger(JUNOS.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (device != null) {
                device.close();
            }
        }

        return pools;
    }

    private String buildNetconfGetAllPoolsRequest() {
        return "<rpc>"
                + "<get-configuration>"
                + "<configuration>"
                + "<access>"
                + "<address-assignment>"
                + "<pool/>"
                + "</address-assignment>"
                + "</access>"
                + "</configuration>"
                + "</get-configuration>"
                + "</rpc>";
    }

    private String buildNetconfEditRequest(String localPoolName, String hexValue) {
        return "<rpc>"
                + "<edit-config>"
                + "<target>"
                + "<candidate/>"
                + "</target>"
                + "<config>"
                + "<configuration>"
                + "<access>"
                + "<address-assignment>"
                + "<pool>"
                + "<name>" + localPoolName + "</name>"
                + "<family>"
                + "<inet>"
                + "<dhcp-attributes>"
                + "<option>"
                + "<id>121</id>"
                + "<hex-string>" + hexValue + "</hex-string>"
                + "</option>"
                + (withOption249
                        ? "<option>"
                        + "<id>249</id>"
                        + "<hex-string>" + hexValue + "</hex-string>"
                        + "</option>" : "")
                + "</dhcp-attributes>"
                + "</inet>"
                + "</family>"
                + "</pool>"
                + "</address-assignment>"
                + "</access>"
                + "</configuration>"
                + "</config>"
                + "</edit-config>"
                + "</rpc>";
    }

    private Map<String, PoolDeviceConfig> parseNetconfResponse(String responseXml, String routerName) {
        Map<String, PoolDeviceConfig> pools = new HashMap<>();
        if (responseXml == null || responseXml.trim().isEmpty()) {
            System.err.println("ERROR: Empty NETCONF response for router " + routerName);
            return pools;
        }

        try {
            String cleanResponseXml = responseXml.replaceAll("<!--.*?-->", "");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(cleanResponseXml)));

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Витягуємо всі pool/name
            XPathExpression poolNamesExpr = xpath.compile("//pool[family/inet]/name");
            NodeList poolNameNodes = (NodeList) poolNamesExpr.evaluate(doc, XPathConstants.NODESET);

            if (globalOptions.isDebug()) {
                System.err.println("Found " + poolNameNodes.getLength() + " pool(s) in NETCONF response for " + routerName);
            }

            for (int i = 0; i < poolNameNodes.getLength(); i++) {

                String localPoolName = poolNameNodes.item(i).getTextContent();
                if (localPoolName == null || localPoolName.trim().isEmpty()) {
                    if (globalOptions.isDebug()) {
                        System.err.println("WARNING: Pool #" + (i + 1) + " has empty <name> in NETCONF response for " + routerName);
                    }
                    continue;
                }

                // Витягуємо default-gateway
                XPathExpression routerExpr = xpath.compile(
                        String.format("//pool[name='%s']/family/inet/dhcp-attributes/router/name", localPoolName)
                );
                String defaultGateway = (String) routerExpr.evaluate(doc, XPathConstants.STRING);

                // Витягуємо опцію 121 (якщо потрібно)
                XPathExpression option121Expr = xpath.compile(
                        String.format("//pool[name='%s']/family/inet/dhcp-attributes/option[name='121']/hex-string", localPoolName)
                );
                String option121 = (String) option121Expr.evaluate(doc, XPathConstants.STRING);

                if (globalOptions.isDebug()) {
                    System.err.println("Parsed pool: " + localPoolName + ", default-gateway: "
                            + (defaultGateway != null ? defaultGateway : "not set")
                            + ", option 121: " + (option121 != null ? option121 : "not set"));
                }

                if (defaultGateway != null && !defaultGateway.trim().isEmpty()) {
                    PoolDeviceConfig poolConfig = new PoolDeviceConfig(defaultGateway, null, option121);
                    // Можна додати option121 до PoolDeviceConfig, якщо потрібно
                    pools.put(localPoolName, poolConfig);
                } else {
                    System.err.println("WARNING: Pool " + localPoolName + " has no default-gateway in NETCONF response for " + routerName);
                }
            }
        } catch (IOException | ParserConfigurationException | DOMException | SAXException e) {
            System.err.println("ERROR: Failed to parse NETCONF response for router " + routerName + ": " + e.getMessage());
        } catch (XPathExpressionException e) {
            System.err.println("ERROR: XPath Expression Exception for router " + routerName + ": " + e.getMessage());
        }
        return pools;
    }

}

/*
    private String buildNetconfGetRequest(String localPoolName) {
        return "<rpc>"
                + "<get-configuration>"
                + " <configuration>"
                + "  <access>"
                + "   <address-assignment>"
                + "    <pool>"
                + "     <name>" + localPoolName + "</name>"
                + "    </pool>"
                + "   </address-assignment>"
                + "  </access>"
                + " </configuration>"
                + "</get-configuration>"
                + "</rpc>";
    }

    private String buildNetconfEditRequest(String localPoolName, String hexValue) {
        return "<rpc>"
                + "<edit-configuration>"
                + " <target>"
                + "  <candidate/>"
                + " </target>"
                + " <config>"
                + "  <access>"
                + "   <address-assignment>"
                + "    <pool>"
                + "     <name>" + localPoolName + "</name>"
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
 */

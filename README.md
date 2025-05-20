# DhcpRouteConverter

[![CI for DhcpRouteConverter](https://github.com/oldengremlin/DhcpRouteConverter/actions/workflows/ci.yml/badge.svg)](https://github.com/oldengremlin/DhcpRouteConverter/actions/workflows/ci.yml)

DhcpRouteConverter is a command-line utility for converting network routes to DHCP options 121 and 249 and vice versa. It supports YAML configuration and multiple DHCP server formats. The tool is compiled into a native binary using GraalVM, making it lightweight and dependency-free.

## Features

- Convert network routes (e.g., `192.168.1.0/24,192.168.0.1`) to DHCP options 121 (and optionally 249) and vice versa.
- Support for multiple DHCP server formats:
  - Default (hex strings for option 121, and 249 with `--with-option-249`).
  - ISC DHCP (`--isc`).
  - MikroTik RouterOS (`--routeros[=<name>]`).
  - Juniper JunOS (`--junos[=<pool-name>]`, default pool: `lan-pool`).
  - Cisco IOS (`--cisco[=<pool-name>]`, default pool: `mypool`).
  - Windows DHCP PowerShell (`--windows`).
- YAML configuration support (`--config=<yaml-file>`).
- Single default route support (`--add-default-gateway=<gateway>`).
- Multi-pool default route support (`--add-default-multi-pool=<pool1:gw1,pool2:gw2,...>`).
- Common routes support (`--common-routes=<network1,gateway1,...>`).
- Warning suppression for missing default route (`--without-warn-no-default-route`).
- Warning for loopback gateways (`--with-warning-loopback`).
- Optional inclusion of DHCP option 249 (`--with-option-249`).
- Enhanced debug mode (`-d`) with detailed logs for route conversion and NETCONF operations.
- Improved NETCONF support for retrieving JunOS configurations, with full application support (`--apply-junos`) still in development.
- Distributed as a `.deb` package for Debian-based systems.
- Native binary with no Java runtime dependency.
- Compatible with any 64-bit x86-64 processor (2003+).

**Note**: NETCONF support (`--apply-junos`) is currently under development and not included in the native binary.

## Installation

### Using the `.deb` Package

1. Download the latest `.deb` package from the [Releases](https://github.com/oldengremlin/DhcpRouteConverter/releases) page.
2. Install the package:
   ```bash
   sudo dpkg -i DhcpRouteConverter_3.0.0_all.deb
   ```
3. Verify installation:
   ```bash
   DhcpRouteConverter -?
   ```

The binary will be installed to `/usr/bin/DhcpRouteConverter`.

## Requirements

- **CPU**: Any 64-bit x86-64 processor (Intel Pentium 4, AMD Athlon 64, or newer; 2003+).
- **OS**: Linux (tested on Ubuntu/Debian).

## Usage

Run `DhcpRouteConverter` with the desired options and arguments. Use `-?` or `--help` to display the help message.

### Options

- `--to-dhcp-options, -tdo [-d] [--isc | --routeros[=<name>] | --junos[=<pool-name>] | --cisco[=<pool-name>] | --windows] [=<network1,gateway1,...>]`  
  Convert network/gateway pairs to DHCP option 121 (and 249 with `--with-option-249`). Use `-d` for debug output of individual routes.  
  Example: `-tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10,172.16.0.0/12,127.0.0.172 --with-option-249`.

- `--from-dhcp-options, -fdo <hex-option>`  
  Decode hexadecimal DHCP option to routes.  
  Example: `-fdo 080a7f00000a0cac107f0000ac`.

- `--config=<yaml-file>`  
  Process routes from YAML configuration. Note: `--apply-junos` is not yet implemented.  
  Example: `--config=routers.yaml`.

- `--add-default-gateway=<gateway>`  
  Add a default route (0.0.0.0/0) for a single pool. Cannot be used with `--add-default-multi-pool`.  
  Example: `--add-default-gateway=94.176.198.17`.

- `--add-default-multi-pool=<pool1:gw1,pool2:gw2,...>`  
  Add default routes for multiple pools (JunOS format). Cannot be used with `--add-default-gateway`.  
  Example: `--add-default-multi-pool=r540pool1:94.176.198.17,r540pool_static1:94.176.199.33`.

- `--common-routes=<network1,gateway1,...>`  
  Specify common routes.  
  Example: `--common-routes=10.0.0.0/8,127.0.0.10`.

- `--without-warn-no-default-route`  
  Suppress warning about missing default route.

- `--with-warning-loopback`  
  Enable warning about gateways in the loopback range (127.0.0.0/8).

- `--with-option-249`  
  Include DHCP option 249 in the output alongside option 121 (default: only option 121).  
  Example: `--with-option-249`.

- `--help, -?`  
  Display the help message.

### Examples

1. **Convert routes to JunOS format with default gateway (only option 121)**:
   ```bash
   DhcpRouteConverter -tdo --junos=r540pool1 --add-default-gateway=94.176.198.17
   ```

2. **Convert routes to JunOS format with both options 121 and 249**:
   ```bash
   DhcpRouteConverter -tdo --junos=r540pool1 --add-default-gateway=94.176.198.17 --with-option-249
   ```

3. **Add default routes for multiple pools**:
   ```bash
   DhcpRouteConverter --add-default-multi-pool=r540pool1:94.176.198.17,r540pool_static1:94.176.199.33
   ```

4. **Process YAML configuration with both options**:
   ```bash
   DhcpRouteConverter --config=routers.yaml --with-option-249
   ```

5. **Convert routes with loopback warning**:
   ```bash
   DhcpRouteConverter -tdo --junos=r540pool1=10.0.0.0/8,127.0.0.10 --with-warning-loopback
   ```

## Releases

### v3.0.0
- **Features**:
  - Added enhanced debug logging for `DhcpOptionConverter` to trace route conversion and NETCONF operations (`-d` flag).
  - Improved NETCONF configuration retrieval for JunOS devices with better error handling and debug output.
  - Added support for consistent default route detection across multiple pool operations.
- **Improvements**:
  - Fixed issue with `hasDefaultRoute` being reset in `generateDhcpOptions` by reusing a single `DhcpOptionConverter` instance in `compareAndUpdatePools` and `generateDhcpOptions`.
  - Optimized GraalVM native image configuration for better performance and reduced binary size.
  - Updated dependencies in `pom.xml` for improved security and compatibility (e.g., `snakeyaml-engine`, `slf4j`, `jsch`).
  - Enhanced input validation for YAML configurations to prevent crashes on malformed inputs.
  - Improved error messages for invalid network or gateway formats.
- **Fixes**:
  - Resolved bug causing incorrect default route warnings due to `hasDefaultRoute` state loss.
  - Fixed NETCONF connection issues with certain JunOS devices by updating SSH configuration.
  - Corrected handling of empty or malformed hex strings in `parseDhcpOptions`.
- **Tests**:
  - Restored and expanded test suite with coverage for `DhcpOptionConverter`, `PoolConfig`, and NETCONF operations.
  - Added tests for edge cases in route conversion and YAML parsing.
- **Known Issues**:
  - NETCONF support (`--apply-junos`) is still under development and may not work with all JunOS versions.
  - Limited support for non-standard DHCP server configurations in `OutputFormatter`.

### v2.0.0
- **Features**:
  - Added YAML configuration support (`--config`).
  - Added single default route support (`--add-default-gateway`).
  - Added multi-pool default route support (`--add-default-multi-pool`).
  - Added common routes support (`--common-routes`).
  - Added warning suppression (`--without-warn-no-default-route`).
  - Added loopback gateway warning (`--with-warning-loopback`).
  - Added optional DHCP option 249 output (`--with-option-249`).
- **Improvements**:
  - Refactored architecture with new classes (`ArgumentParser`, `DhcpOptionConverter`, etc.).
  - Added basic test suite.
  - Standardized command-line syntax with `=` for arguments.
- **Fixes**:
  - Improved input validation for YAML and command-line arguments.
  - Fixed route aggregation for combined options.
- **Known Issues**:
  - NETCONF support (`--apply-junos`) is not yet implemented.

## Building from Source

### Prerequisites

- GraalVM JDK 21 with `native-image` installed.
- Maven 3.6 or higher.

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/oldengremlin/DhcpRouteConverter.git
   cd DhcpRouteConverter
   ```

2. Set `JAVA_HOME` to GraalVM:
   ```bash
   export JAVA_HOME=/path/to/graalvm-jdk-21
   ```

3. Build the project:
   ```bash
   mvn clean package
   ```

4. Find the native binary at `target/DhcpRouteConverter` and the `.deb` package at `target/DhcpRouteConverter_3.0.0_all.deb`.

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.
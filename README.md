# DhcpRouteConverter

[![CI for DhcpRouteConverter](https://github.com/oldengremlin/DhcpRouteConverter/actions/workflows/ci.yml/badge.svg)](https://github.com/oldengremlin/DhcpRouteConverter/actions/workflows/ci.yml)

DhcpRouteConverter is a command-line utility for converting network routes to DHCP options 121 and 249 and vice versa. It supports generating hexadecimal DHCP options from network/gateway pairs, decoding hexadecimal options back to human-readable routes, and generating configuration snippets for popular DHCP servers. The tool is compiled into a native binary using GraalVM, making it lightweight and dependency-free on target systems.

## Features

- Convert network routes (e.g., `192.168.1.0/24,192.168.0.1`) to DHCP options 121/249 and vice versa.
- Support for multiple DHCP server formats:
  - Default (hex strings for options 121/249).
  - ISC DHCP (`--isc`).
  - MikroTik RouterOS (`--routeros`).
  - Juniper JunOS (`--junos` or `--junos=pool-name`, default pool: `lan-pool`).
  - Cisco IOS (`--cisco` or `--cisco=pool-name`, default pool: `mypool`).
  - Windows DHCP PowerShell (`--windows`).
- Debug mode (`-d`) to display individual route options.
- Optional `0x` prefix support for hexadecimal input.
- Distributed as a `.deb` package for easy installation on Debian-based systems.
- Native binary with no Java runtime dependency.
- Compatible with any 64-bit x86-64 processor (Intel Pentium 4, AMD Athlon 64, or newer; 2003+).

## Installation

### Using the `.deb` Package

1. Download the latest `.deb` package from the [Releases](https://github.com/oldengremlin/DhcpRouteConverter/releases) page.
2. Install the package using `dpkg`:
   ```bash
   sudo dpkg -i DhcpRouteConverter_1.2.0_all.deb
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

- `--to-dhcp-options`, `-tdo [-d] [--isc | --routeros | --junos[=pool-name] | --cisco[=pool-name] | --windows] <network1,gateway1,network2,gateway2,...>`  
  Convert a comma-separated list of network/gateway pairs to DHCP options.  
  Use `-d` to display individual route options.  
  Specify a format:  
  - `--isc`: ISC DHCP server format.  
  - `--routeros`: MikroTik RouterOS format.  
  - `--junos` or `--junos=pool-name`: Juniper JunOS format (default pool: `lan-pool`).  
  - `--cisco` or `--cisco=pool-name`: Cisco IOS format (default pool: `mypool`).  
  - `--windows`: Windows DHCP PowerShell format.  
  Default output is `aggregate_opt_121/249` hex strings.  
  Example: `192.168.1.0/24,192.168.0.1,10.0.0.0/8,10.0.0.1`.

- `--from-dhcp-options`, `-fdo <hex-option>`  
  Decode a hexadecimal DHCP option string to network/gateway pairs. Supports optional `0x` prefix.  
  Example: `18c0a801c0a80001` or `0x18c0a801c0a80001`.

- `--help`, `-?`  
  Display the help message.

### Examples

1. **Convert network routes to default DHCP options**:
   ```bash
   DhcpRouteConverter -tdo 192.168.0.0/16,127.0.0.192,172.16.0.0/12,127.0.0.172,10.0.0.0/8,127.0.0.10
   ```
   Output:
   ```
   aggregate_opt_121 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a
   aggregate_opt_249 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a
   ```

2. **Convert with debug output**:
   ```bash
   DhcpRouteConverter -tdo -d 192.168.0.0/16,127.0.0.192
   ```
   Output:
   ```
   option_121_route_192.168.0.0/16_via_127.0.0.192 : 0x10c0a87f0000c0
   option_249_route_192.168.0.0/16_via_127.0.0.192 : 0x10c0a87f0000c0
   aggregate_opt_121 : 0x10c0a87f0000c0
   aggregate_opt_249 : 0x10c0a87f0000c0
   ```

3. **Convert to ISC DHCP server format**:
   ```bash
   DhcpRouteConverter -tdo --isc 192.168.0.0/16,127.0.0.192
   ```
   Output:
   ```
   option rfc3442-classless-static-routes code 121 = array of unsigned integer 8;
   option rfc3442-classless-static-routes 16,192,168,127,0,0,192;
   option ms-classless-static-routes code 249 = array of unsigned integer 8;
   option ms-classless-static-routes 16,192,168,127,0,0,192;
   ```

4. **Convert to MikroTik RouterOS format**:
   ```bash
   DhcpRouteConverter -tdo --routeros 192.168.0.0/16,127.0.0.192
   ```
   Output:
   ```
   /ip dhcp-server option add code=121 name=aggregate_opt_121 value=0x10c0a87f0000c0
   /ip dhcp-server option add code=249 name=aggregate_opt_249 value=0x10c0a87f0000c0
   ```

5. **Convert to Juniper JunOS format with custom pool**:
   ```bash
   DhcpRouteConverter -tdo --junos=vlan100-pool 192.168.0.0/16,127.0.0.192
   ```
   Output:
   ```
   set access address-assignment pool vlan100-pool family inet dhcp-attributes option 121 hex-string 10c0a87f0000c0
   set access address-assignment pool vlan100-pool family inet dhcp-attributes option 249 hex-string 10c0a87f0000c0
   ```

6. **Convert to Cisco IOS format with custom pool**:
   ```bash
   DhcpRouteConverter -tdo --cisco=custom-pool 192.168.0.0/16,127.0.0.192
   ```
   Output:
   ```
   ip dhcp pool custom-pool
    option 121 hex 10c0a87f0000c0
    option 249 hex 10c0a87f0000c0
   ```

7. **Decode DHCP options to routes**:
   ```bash
   DhcpRouteConverter -fdo 0x10c0a87f0000c0
   ```
   Output:
   ```
   192.168.0.0/16 via 127.0.0.192
   ```

## Releases

### v1.2.0
- **Features**:
  - Added support for Cisco IOS format (`--cisco` or `--cisco=pool-name`, default pool: `mypool`).
  - Added support for Windows DHCP PowerShell format (`--windows`).
  - Added custom pool name support for JunOS (`--junos=pool-name`, default: `lan-pool`).
  - Added validation for mutually exclusive format arguments (e.g., cannot combine `--isc` and `--cisco`).
- **Fixes**:
  - Fixed `maven-install-plugin` error by explicitly setting the main JAR artifact.
  - Added empty `conffiles` to suppress `jdeb` warning during `.deb` packaging.
  - Automated Debian package version in `control` file using `jdeb` variables to sync with `pom.xml`.
- **Improvements**:
  - Enhanced CI with QEMU-based testing for `.deb` package deployment.
  - Improved test coverage for Cisco and JunOS custom pool names (28 tests total).

### v1.1.0
- **Features**:
  - Improved CPU compatibility for older x86-64 processors (`-march=x86-64`).
- **Fixes**:
  - Fixed GitHub Actions workflow for consistent builds.
  - Removed `maven-surefire-plugin` deprecated warning.
- **Improvements**:
  - Optimized native binary size with GraalVM.

### v1.0.0
- **Features**:
  - Initial release with support for default, ISC DHCP, MikroTik RouterOS, and Juniper JunOS formats.
  - Conversion of network routes to DHCP options 121/249 and vice versa.
  - Debug mode (`-d`) for individual route options.
  - Native binary compilation with GraalVM.
  - `.deb` package generation for Debian-based systems.
- **Notes**:
  - Basic test suite with 20 unit tests.
  - CI pipeline with GitHub Actions for automated testing and builds.

## Testing

The project includes unit tests written with JUnit 5 to ensure the reliability of the core functionality. Tests cover the `generateDhcpOptions`, `parseDhcpOptions`, `toHex`, and `main` methods, including valid and invalid inputs, as well as format-specific outputs for all supported DHCP servers (ISC, RouterOS, JunOS, Cisco, Windows).

To run the tests locally:
```bash
mvn test
```

The tests are automatically executed during the build process (`mvn package`) and in the CI pipeline via GitHub Actions.

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

4. Find the native binary at `target/DhcpRouteConverter` and the `.deb` package at `target/DhcpRouteConverter_1.2.0_all.deb`.

## Continuous Integration

The project uses GitHub Actions for continuous integration. Every push or pull request to the `master` branch triggers a workflow that:
- Runs all unit tests (28 tests).
- Builds the native binary and `.deb` package.
- Tests the `.deb` package deployment using QEMU.
- Uploads the `.deb` as an artifact.

Check the [Actions](https://github.com/oldengremlin/DhcpRouteConverter/actions) tab for build status and artifacts.

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please submit issues or pull requests on the [GitHub repository](https://github.com/oldengremlin/DhcpRouteConverter).
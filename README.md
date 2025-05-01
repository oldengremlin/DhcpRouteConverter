# DhcpRouteConverter

DhcpRouteConverter is a command-line utility for converting network routes to DHCP options 121 and 249 and vice versa. It supports generating hexadecimal DHCP options from network/gateway pairs and decoding hexadecimal options back to human-readable routes. The tool is compiled into a native binary using GraalVM, making it lightweight and dependency-free on target systems.

## Features

- Convert network routes (e.g., `192.168.1.0/24,192.168.0.1`) to DHCP options 121/249.
- Decode hexadecimal DHCP options (e.g., `0x18c0a801c0a80001`) to network routes.
- Support for optional `0x` prefix in hexadecimal input.
- Debug mode (`-d`) to display individual route options.
- Distributed as a `.deb` package for easy installation on Debian-based systems.
- Native binary with no Java runtime dependency.

## Installation

### Using the `.deb` Package

1. Download the latest `.deb` package from the [Releases](https://github.com/oldengremlin/DhcpRouteConverter/releases) page.
2. Install the package using `dpkg`:
   ```bash
   sudo dpkg -i DhcpRouteConverter_1.0.0_all.deb
   ```
3. Verify installation:
   ```bash
   DhcpRouteConverter -?
   ```

The binary will be installed to `/usr/bin/DhcpRouteConverter`.

### Requirements

- Debian-based system (e.g., Ubuntu, Debian).
- `libc` (standard C library, typically pre-installed).

## Usage

Run `DhcpRouteConverter` with the desired options and arguments. Use `-?` or `--help` to display the help message.

### Options

- `--to-dhcp-options`, `-tdo [-d] <network1,gateway1,network2,gateway2,...>`  
  Convert a comma-separated list of network/gateway pairs to DHCP options. Use `-d` to display individual route options.
- `--from-dhcp-options`, `-fdo <hex-option>`  
  Decode a hexadecimal DHCP option string to network/gateway pairs. Supports `0x` prefix.
- `--help`, `-?`  
  Display the help message.

### Examples

1. **Convert network routes to DHCP options**:
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
   DhcpRouteConverter -tdo -d 192.168.0.0/16,127.0.0.192,172.16.0.0/12,127.0.0.172,10.0.0.0/8,127.0.0.10
   ```
   Output:
   ```
   option_121_route_192.168.0.0/16_via_127.0.0.192 : 0x10c0a87f0000c0
   option_249_route_192.168.0.0/16_via_127.0.0.192 : 0x10c0a87f0000c0
   option_121_route_172.16.0.0/12_via_127.0.0.172 : 0x0cac107f0000ac
   option_249_route_172.16.0.0/12_via_127.0.0.172 : 0x0cac107f0000ac
   option_121_route_10.0.0.0/8_via_127.0.0.10 : 0x080a7f00000a
   option_249_route_10.0.0.0/8_via_127.0.0.10 : 0x080a7f00000a
   aggregate_opt_121 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a
   aggregate_opt_249 : 0x10c0a87f0000c00cac107f0000ac080a7f00000a
   ```

3. **Decode DHCP options to routes**:
   ```bash
   DhcpRouteConverter -fdo 0x10c0a87f0000c00cac107f0000ac080a7f00000a
   ```
   Output:
   ```
   192.168.0.0/16 via 127.0.0.192
   172.16.0.0/12 via 127.0.0.172
   10.0.0.0/8 via 127.0.0.10
   ```

   Without `0x` prefix:
   ```bash
   DhcpRouteConverter -fdo 10c0a87f0000c00cac107f0000ac080a7f00000a
   ```
   Output is the same.

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

4. Find the native binary at `target/DhcpRouteConverter` and the `.deb` package at `target/DhcpRouteConverter_1.0.0_all.deb`.

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please submit issues or pull requests on the [GitHub repository](https://github.com/oldengremlin/DhcpRouteConverter).

# WGCTRL: WireGuard API Service

The Service provides an API for controlling **WireGuard** servers.
It allows you to manage WireGuard nodes and automatically
balance peers by nodes according locations and load.

## Prerequisites

Before you can use the Wireguard API service, you will need to have the following installed on your system:

- [Clojure](https://clojure.org/)
- [Leiningen](https://leiningen.org/) 
- [Wireguard](https://www.wireguard.com/)

The Service API explores SSH to command WG Nodes

## Installation

To install the Wireguard API service, clone the repository and install the dependencies:

```bash
git clone https://github.com/nezavisimost/wgctrl.git
cd wgctrl
lein repl 
```

The service by default listening 8080 (HTTP API) and 7888 (nRepl) ports

## Usage

You can then use the API by sending HTTP requests to the service. The API supports the following endpoints:

    GET /peer?location=XX: Creates new peer
    GET /locations: List of all locations 
    GET /stat: Statistic by node, amount of peers
    
For example, to list all Wireguard Nodes locations, 
you can send a GET request to `http://localhost:8080/locations`

### Responses 

The API has next responses:

- ``/peer?location=XX``   
The response is a map for WG configuration 
```json 
{
  "interface": {
    "address": "10.7.0.16/24",
    "key": "wCAndMWDHZpOT138xxxxxx8BBLqoXhqj649+WitT5y7F8=",
    "dns": "1.1.1.1, 1.0.0.1"
  },
  "peer": {
    "pubkey": "DXn0oXV5/5fCtgKlxxxxxqKkECX/wibquJYX6/9wCASM=",
    "psk": "BRjawrMPOwMzShGHxtfMj8g5rfcPzUzqLs1wpifmp+c=",
    "allowed_ips": "0.0.0.0/0",
    "endpoint": "94.176.X.Y:51820"
  }
}
```

- ``/stat``

```json
{
  "uuid": "75aca2c7-50c9-400a-9001-a0951210fbf1",
  "nodes": [
    {
      "node": "79d2843f-15b2-4484-8612-c570a8bdfe24",
      "hostname": "dev1.vpn.dev",
      "stat": [
        {
          "name": "wg0",
          "peers": 15
        }
      ]
    }
  ]
}     
```

- ``/location`` 

```json
{
  "locations": [
    {
      "code": "dev",
      "name": "🏴‍☠️ Development"
    }
  ]
}

```
### Errors

Errors looks loke JSON formatted message, http code is 200, but error code inside the JSON message.
Note: "message" is the real error message from System. 

```json
{
  "code": 10,
  "err": "Can't create peer",
  "message": "Node or Interface for test not found"
}
```


## Node prepare 

On the wg-node you should have next packages installed and configured:

- WireGuard 
- Babashka 
- SSH 

First prepare node: 

```bash 
cd wgctrl
ssh user@server 'bb'  < scripts/node-register.bb
```

The script will create ~/.wg-node edn-formatted file with system information about the node

Example `~/.wg-node`: 

```bash
{:uuid "79d2843f-15b2-4484-8612-c570a8bdfe24", :hostname "dev1.vpn.dev", :default-interface "ens3", :interfaces [{:name "wg0", :subnet {:inet "10.7.0.1/24,", :inet6 "fddd:2c4:2c4:2c4::1/64"}, :port "51820", :public-key "DXn0oXV5/5fCtgKlf9VjqKkECX/wibquJYX6/9wCASM=", :endpoint {:inet "94.176.X.Y", :inet6 "2a02:7b40:5eb0:eedc::1"}}]}
```

# Contributing

We welcome contributions to the Wireguard API service! If you have an idea for a new feature or have found a bug, please open an [**Issue**](https://github.com/nezavisimost/wgctrl/issues) or a [**Pull Request**](https://github.com/nezavisimost/wgctrl/pulls) on the [**GitHub repository**](https://github.com/nezavisimost/wgctrl).

# License

The **WireGuard API** service is licensed under the GPL-V3 License.

# Acknowledgements
- [**2pizza**](https://github.com/the2pizza)




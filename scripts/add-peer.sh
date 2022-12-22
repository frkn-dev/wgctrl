#!/bin/bash

# This script generates a wireguard public key, adds a peer to the wireguard configuration,
# and generates a qr-code for the client configuration

# Check if wireguard and qrencode are installed
if ! command -v wg &> /dev/null || ! command -v qrencode &> /dev/null
then
    echo "Error: wireguard and/or qrencode are not installed" >&2
    exit 1
fi

# Check if the required arguments are provided
if [ $# -ne 6 ]
then
    echo "Usage: $0 <interface> <peer-ip> <allowed-ipv4> <allowed-ipv6> <peer_name" >&2
    exit 1
fi

# Get the interface, peer IP, and allowed IP from the arguments
interface=$1
peer_ip=$2
allowed_ipv4=$3
allowed_ipv6=$4
peer_name=$5
endpoint_port=$6

# Check if the interface exists
if ! wg show "$interface" &> /dev/null
then
    echo "Error: interface $interface does not exist" >&2
    exit 1
fi

dns="1.1.1.1, 1.0.0.1"


new_client_setup() {
    
    key=$(wg genkey)
    psk=$(wg genpsk)

    echo $psk > psk.key
    public_key=$(wg pubkey <<< "$key")

    # Configure client in the server

    wg set $interface peer \
           $(wg pubkey <<< "$key")\
            allowed-ips "$allowed_ipv4, $allowed_ipv6"\
            preshared-key psk.key\
            endpoint $peer_ip:$endpoint_port



echo "{:name $peer_name, 
       :psk $psk,
       :allowed-ips [$allowed_ipv4 $allowed_ipv6],
       :interface $interface,
       :endpoint $peer_ip:$endpoint_port,
       :public-key $public_key, 
       :private-key $key,
       :dns $dns}"


new_client_setup

# Generate a qr-code for the client configuration
#qrencode -t UTF8 < $peer_name.conf
exit 0
#echo "Success: added peer $public_key to $interface"
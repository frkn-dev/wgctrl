#!/bin/bash


# Tweak WG Interface /24 -> 16 subnet 
sed -i 's|Address = 10.8.1.0/24|Address = 10.8.0.0/16|' /opt/amnezia/wireguard/wg0.conf

# Tweak Iptables /24 -> 16 subnet
sed  -i 's|10.8.1.0/24|10.8.0.0/16|g' /opt/amnezia/start.sh


# Restart WG 
wg-quick down /opt/amnezia/wireguard/wg0.conf
wg-quick up /opt/amnezia/wireguard/wg0.conf

exit 0

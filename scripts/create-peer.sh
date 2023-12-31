#!/bin/bash

ip=$1
psk=$2 

mkdir -p /opt/keys 
cd /opt/keys

psk="/opt/amnezia/wireguard/wireguard_psk.key"

umask 077

postfix=$(tr -dc A-Za-z0-9 </dev/urandom | head -c 13; echo)

wg genkey > key-${postfix} 
wg pubkey < key-${postfix} > key-${postfix}.pubkey

wg set wg0 peer $(cat key-${postfix}.pubkey) preshared-key $psk allowed-ips $ip

echo -n {:private \"$(cat key-${postfix})\" :peer \"$(cat key-${postfix}.pubkey)\" :ip \"$ip\"}


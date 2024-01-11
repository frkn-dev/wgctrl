#!/bin/bash


peer=$1
ip=$2

psk="/opt/amnezia/wireguard/wireguard_psk.key"


wg set wg0 peer "${peer}" preshared-key $psk allowed-ips $ip
  wg_exit_status=$?

if [ $wg_exit_status -eq 0 ]; then
    echo -n {:peer \"$peer\" :ip \"$ip\"}
else
    echo -n {:error \"Error - wg command failed for peer $peer\"  :status \"$wg_exit_status\"}
fi

exit $wg_exit_status

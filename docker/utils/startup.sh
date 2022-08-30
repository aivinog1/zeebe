#!/bin/bash -xeu

HOST=$(hostname -i)

if [ "$ZEEBE_STANDALONE_GATEWAY" = "true" ]; then
    export ZEEBE_GATEWAY_NETWORK_HOST=${ZEEBE_GATEWAY_NETWORK_HOST:-${HOST}}
    export ZEEBE_GATEWAY_CLUSTER_HOST=${ZEEBE_GATEWAY_CLUSTER_HOST:-${ZEEBE_GATEWAY_NETWORK_HOST}}

    export OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME:=zeebe-gateway}

    exec /usr/local/zeebe/bin/gateway
else
    export ZEEBE_BROKER_NETWORK_HOST=${ZEEBE_BROKER_NETWORK_HOST:-${HOST}}
    export ZEEBE_BROKER_GATEWAY_CLUSTER_HOST=${ZEEBE_BROKER_GATEWAY_CLUSTER_HOST:-${ZEEBE_BROKER_NETWORK_HOST}}

    export OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME:=zeebe-broker}

    exec /usr/local/zeebe/bin/broker
fi

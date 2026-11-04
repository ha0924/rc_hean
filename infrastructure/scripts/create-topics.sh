#!/usr/bin/env sh
set -eu

docker compose exec -T rocketmq-broker sh mqadmin updateTopic \
  -n rocketmq-namesrv:9876 \
  -c DefaultCluster \
  -t notification-delivery \
  -r 8 \
  -w 8

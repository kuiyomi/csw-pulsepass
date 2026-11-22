#!/bin/sh
set -eu
while read -r topic partitions; do
    retention=604800000
    if [ "$topic" = telemetry.navigation ]; then retention=86400000; fi
    kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic "$topic" \
        --replication-factor 1 --partitions "$partitions" --config "retention.ms=$retention"
    kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic "$topic.DLT" \
        --replication-factor 1 --partitions "$partitions" --config retention.ms=1209600000
done <<'TOPICS'
inventory.seats-reserved 12
inventory.seats-released 12
inventory.reservation-expired 12
order.created 6
order.paid 6
order.expired 6
ticket.issued 6
iam.organizer-requested 3
iam.organizer-approved 3
telemetry.navigation 6
analytics.metrics 3
TOPICS

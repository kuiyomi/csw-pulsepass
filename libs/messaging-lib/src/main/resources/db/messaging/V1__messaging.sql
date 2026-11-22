CREATE TABLE outbox_event (
    sequence_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    event_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    envelope jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    published_at timestamptz
);
CREATE INDEX outbox_pending_aggregate ON outbox_event (aggregate_id, sequence_id)
    WHERE published_at IS NULL;

CREATE TABLE processed_message (
    consumer_group text NOT NULL,
    event_id uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (consumer_group, event_id)
);

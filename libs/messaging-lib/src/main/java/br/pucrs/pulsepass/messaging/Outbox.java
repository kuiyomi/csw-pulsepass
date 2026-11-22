package br.pucrs.pulsepass.messaging;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Call append on the same database connection used to change the aggregate. */
public final class Outbox {
    public record Event(UUID eventId, String eventType, UUID aggregateId, String envelope) {}

    @FunctionalInterface
    public interface Publisher {
        /** Returns after the broker acknowledges the record, or throws on uncertain delivery. */
        void publish(Event event) throws Exception;
    }

    public void append(Connection transaction, Event event) throws SQLException {
        requireTransaction(transaction);
        try (var insert = transaction.prepareStatement("""
                INSERT INTO outbox_event(event_id, event_type, aggregate_id, envelope)
                VALUES (?, ?, ?, CAST(? AS jsonb))
                """)) {
            insert.setObject(1, event.eventId());
            insert.setString(2, event.eventType());
            insert.setObject(3, event.aggregateId());
            insert.setString(4, event.envelope());
            insert.executeUpdate();
        }
    }

    /** The caller commits after success and rolls back on any exception. */
    public boolean publishNext(Connection transaction, Publisher publisher) throws Exception {
        requireTransaction(transaction);
        // Earlier unpublished events block later events for this aggregate, even with multiple relays.
        try (var query = transaction.prepareStatement("""
                SELECT o.event_id, o.event_type, o.aggregate_id, o.envelope
                FROM outbox_event o
                WHERE o.published_at IS NULL AND NOT EXISTS (
                    SELECT 1 FROM outbox_event prior
                    WHERE prior.aggregate_id = o.aggregate_id AND prior.sequence_id < o.sequence_id
                      AND prior.published_at IS NULL
                )
                ORDER BY o.sequence_id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                """); var rows = query.executeQuery()) {
            if (!rows.next()) {
                return false;
            }
            var event = new Event(rows.getObject(1, UUID.class), rows.getString(2),
                    rows.getObject(3, UUID.class), rows.getString(4));
            publisher.publish(event);
            try (var update = transaction.prepareStatement("""
                    UPDATE outbox_event SET published_at = clock_timestamp() WHERE event_id = ?
                    """)) {
                update.setObject(1, event.eventId());
                update.executeUpdate();
            }
            return true;
        }
    }

    static void requireTransaction(Connection connection) throws SQLException {
        if (connection.getAutoCommit()) {
            throw new IllegalStateException("É necessária uma transação explícita.");
        }
    }
}

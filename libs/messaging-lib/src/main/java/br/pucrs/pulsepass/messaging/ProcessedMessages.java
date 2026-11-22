package br.pucrs.pulsepass.messaging;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public final class ProcessedMessages {
    @FunctionalInterface
    public interface DatabaseEffect {
        void apply(Connection transaction) throws SQLException;
    }

    /** Caller commits the DB before acknowledging the Kafka offset; on error it rolls back. */
    public boolean applyOnce(Connection transaction, String consumerGroup, UUID eventId,
                             DatabaseEffect effect) throws SQLException {
        Outbox.requireTransaction(transaction);
        try (var insert = transaction.prepareStatement("""
                INSERT INTO processed_message(consumer_group, event_id) VALUES (?, ?)
                ON CONFLICT (consumer_group, event_id) DO NOTHING
                """)) {
            insert.setString(1, consumerGroup);
            insert.setObject(2, eventId);
            if (insert.executeUpdate() == 0) {
                return false;
            }
        }
        effect.apply(transaction);
        return true;
    }
}

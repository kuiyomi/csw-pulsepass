package br.pucrs.pulsepass.inventory.adapter.out;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Caller owns the reservation, outbox and allocation in one transaction.
 * Roll back the whole transaction on conflict. */
public final class SeatAllocationRepository {
    public void allocate(Connection tx, UUID eventId, UUID reservationId, List<UUID> seats)
            throws SQLException {
        requireTransaction(tx);
        if (seats.isEmpty() || seats.size() > 8 || new HashSet<>(seats).size() != seats.size()
                || seats.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Selecione de um a oito assentos distintos.");
        }
        // Stable lock order prevents circular waits between overlapping selections.
        try (var update = tx.prepareStatement("""
                UPDATE seat s SET status = 'RESERVADO', reservation_id = ?
                FROM sector sec, reservation r
                WHERE s.id = ? AND sec.id = s.sector_id AND sec.event_id = ?
                  AND r.id = ? AND r.event_id = sec.event_id AND r.status = 'ATIVA'
                  AND r.expires_at > clock_timestamp() AND s.status = 'LIVRE'
                """)) {
            for (var seat : seats.stream().sorted().toList()) {
                update.setObject(1, reservationId);
                update.setObject(2, seat);
                update.setObject(3, eventId);
                update.setObject(4, reservationId);
                if (update.executeUpdate() != 1) {
                    throw new SeatConflictException(seat);
                }
            }
        }
    }

    public boolean confirm(Connection tx, UUID reservationId) throws SQLException {
        return finish(tx, reservationId, "CONFIRMADA", "expires_at > clock_timestamp()");
    }

    public boolean expire(Connection tx, UUID reservationId) throws SQLException {
        return finish(tx, reservationId, "EXPIRADA", "expires_at <= clock_timestamp()");
    }

    public boolean cancel(Connection tx, UUID reservationId) throws SQLException {
        return finish(tx, reservationId, "CANCELADA", "true");
    }

    private boolean finish(Connection tx, UUID reservationId, String next, String timeCondition)
            throws SQLException {
        requireTransaction(tx);
        // Lock first, then evaluate the deadline using database time after any wait.
        try (var lock = tx.prepareStatement("SELECT id FROM reservation WHERE id = ? FOR UPDATE")) {
            lock.setObject(1, reservationId);
            try (var rows = lock.executeQuery()) {
                if (!rows.next()) {
                    return false;
                }
            }
        }
        try (var transition = tx.prepareStatement(
                "UPDATE reservation SET status = ? WHERE id = ? AND status = 'ATIVA' AND " + timeCondition)) {
            transition.setString(1, next);
            transition.setObject(2, reservationId);
            if (transition.executeUpdate() == 0) {
                return false;
            }
        }
        boolean sold = next.equals("CONFIRMADA");
        String mutation = sold ? "status = 'VENDIDO'" : "status = 'LIVRE', reservation_id = NULL";
        try (var update = tx.prepareStatement(
                "UPDATE seat SET " + mutation + " WHERE reservation_id = ? AND status = 'RESERVADO'")) {
            update.setObject(1, reservationId);
            update.executeUpdate();
        }
        return true;
    }

    private static void requireTransaction(Connection tx) throws SQLException {
        if (tx.getAutoCommit()) {
            throw new IllegalStateException("A alocação exige transação explícita.");
        }
    }

    public static final class SeatConflictException extends IllegalStateException {
        public SeatConflictException(UUID seatId) {
            super("Assento indisponível: " + seatId);
        }
    }
}

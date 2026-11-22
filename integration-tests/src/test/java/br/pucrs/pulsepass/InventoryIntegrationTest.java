package br.pucrs.pulsepass;

import br.pucrs.pulsepass.inventory.adapter.out.SeatAllocationRepository;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class InventoryIntegrationTest {
    @Test
    void oneWinnerAcrossFiveHundredOverlappingAttempts() throws Exception {
        var repo = new SeatAllocationRepository();
        var event = UUID.randomUUID();
        var sector = UUID.randomUUID();
        var seat = UUID.randomUUID();
        try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            try (var setup = connect(pg); var sql = setup.createStatement();
                 var migration = getClass().getResourceAsStream("/db/migration/V2__inventory.sql")) {
                sql.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
                sql.execute("INSERT INTO venue_event VALUES ('" + event + "',gen_random_uuid(),'Show','Ao vivo',now(),'POA','PUBLICADO',NULL)");
                sql.execute("INSERT INTO sector VALUES ('" + sector + "','" + event + "','Pista',1000,false)");
                sql.execute("INSERT INTO seat VALUES ('" + seat + "','" + sector + "','A',1,'LIVRE',NULL)");
            }
            var start = new CountDownLatch(1);
            var attempts = new ArrayList<Future<Boolean>>();
            // Bound live DB sessions while all 500 clients contend for the same inventory.
            var sessions = new java.util.concurrent.Semaphore(24);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 500; i++) {
                    attempts.add(workers.submit(() -> {
                        start.await();
                        sessions.acquire();
                        try (var tx = connect(pg)) {
                            tx.setAutoCommit(false);
                            var reservation = UUID.randomUUID();
                            try (var create = tx.prepareStatement("""
                                    INSERT INTO reservation VALUES (?, ?, gen_random_uuid(), 'ATIVA',
                                        now(), now() + interval '10 minutes')
                                    """)) {
                                create.setObject(1, reservation);
                                create.setObject(2, event);
                                create.executeUpdate();
                                repo.allocate(tx, event, reservation, List.of(seat));
                                tx.commit();
                                return true;
                            } catch (SeatAllocationRepository.SeatConflictException conflict) {
                                tx.rollback();
                                return false;
                            }
                        } finally {
                            sessions.release();
                        }
                    }));
                }
                start.countDown();
                int winners = 0;
                for (var attempt : attempts) {
                    if (attempt.get()) winners++;
                }
                assertEquals(1, winners);
            }
            try (var tx = connect(pg); var sql = tx.createStatement();
                 var rows = sql.executeQuery("SELECT reservation_id FROM seat WHERE status = 'RESERVADO'")) {
                assertTrue(rows.next());
                var winner = rows.getObject(1, UUID.class);
                tx.setAutoCommit(false);
                assertFalse(repo.expire(tx, winner));
                assertTrue(repo.confirm(tx, winner));
                assertFalse(repo.cancel(tx, winner));
                tx.commit();
            }
        }
    }

    static Connection connect(PostgreSQLContainer<?> db) throws SQLException {
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }
}

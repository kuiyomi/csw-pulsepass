package br.pucrs.pulsepass.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.*;

public class DomainEvents {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final KafkaTemplate<String,String> kafka;
    private final Outbox outbox = new Outbox();

    public DomainEvents(JdbcTemplate db, ObjectMapper json, KafkaTemplate<String,String> kafka) {
        this.db = db;
        this.json = json;
        this.kafka = kafka;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String type, UUID aggregate, Map<String,?> payload) {
        UUID id = UUID.randomUUID();
        try {
            String envelope = json.writeValueAsString(Map.of("eventId", id, "eventType", type,
                    "occurredAt", Instant.now().toString(), "aggregateId", aggregate, "version", 1, "payload", payload));
            db.execute((ConnectionCallback<Void>) connection -> {
                outbox.append(connection, new Outbox.Event(id, type, aggregate, envelope));
                return null;
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar evento", ex);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.delay-ms:100}")
    @Transactional
    public void relay() {
        db.execute((ConnectionCallback<Boolean>) connection -> {
            try {
                return outbox.publishNext(connection, e -> kafka.send(e.eventType(), e.aggregateId().toString(),
                        e.envelope()).get(5, TimeUnit.SECONDS));
            } catch (Exception ex) { throw new IllegalStateException("Falha no relay da outbox", ex); }
        });
    }
}

package br.pucrs.pulsepass;

import br.pucrs.pulsepass.messaging.Outbox;
import br.pucrs.pulsepass.messaging.ProcessedMessages;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MessagingIntegrationTest {
    @Test
    void retriesUnacknowledgedPublishAndAppliesDuplicateOnlyOnce() throws Exception {
        var outbox = new Outbox();
        var processed = new ProcessedMessages();
        try (var pg = new PostgreSQLContainer<>("postgres:16-alpine");
             var kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))) {
            pg.start();
            kafka.start();
            try (var tx = InventoryIntegrationTest.connect(pg); var sql = tx.createStatement();
                 var migration = getClass().getResourceAsStream("/db/messaging/V1__messaging.sql")) {
                sql.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
                sql.execute("CREATE TABLE receipt (id uuid PRIMARY KEY)");
                tx.setAutoCommit(false);
                var id = UUID.randomUUID();
                var event = new Outbox.Event(id, "order.paid", UUID.randomUUID(), "{\"eventId\":\"" + id + "\"}");
                outbox.append(tx, event);
                tx.rollback();
                assertFalse(outbox.publishNext(tx, ignored -> fail("Evento revertido")));
                outbox.append(tx, event);
                tx.commit();
                assertThrows(java.io.IOException.class, () -> outbox.publishNext(tx, ignored -> {
                    throw new java.io.IOException("Sem confirmação do broker");
                }));
                tx.rollback();

                var props = new Properties();
                props.put("bootstrap.servers", kafka.getBootstrapServers());
                props.put("acks", "all");
                props.put("enable.idempotence", "true");
                try (var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
                    assertTrue(outbox.publishNext(tx, row -> producer.send(new ProducerRecord<>(
                            row.eventType(), row.aggregateId().toString(), row.envelope())).get(30, TimeUnit.SECONDS)));
                    tx.commit();
                }
                props.remove("acks");
                props.remove("enable.idempotence");
                props.put("group.id", "ticket-service");
                props.put("auto.offset.reset", "earliest");
                props.put("enable.auto.commit", "false");
                try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
                    consumer.subscribe(List.of(event.eventType()));
                    var deadline = Instant.now().plusSeconds(45);
                    boolean received = false;
                    while (!received && Instant.now().isBefore(deadline)) {
                        for (var record : consumer.poll(Duration.ofSeconds(1))) {
                            assertEquals(event.aggregateId().toString(), record.key());
                            assertEquals(id.toString(), new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(record.value()).get("eventId").asText());
                            for (int i = 0; i < 3; i++) {
                                assertEquals(i == 0, processed.applyOnce(tx, "ticket-service", id, connection -> {
                                    try (var insert = connection.prepareStatement("INSERT INTO receipt VALUES (?)")) {
                                        insert.setObject(1, id);
                                        insert.executeUpdate();
                                    }
                                }));
                                tx.commit();
                            }
                            received = true;
                        }
                    }
                    assertTrue(received);
                    consumer.commitSync();
                }
            }
        }
    }
}

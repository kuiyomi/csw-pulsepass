package br.pucrs.pulsepass;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisIntegrationTest {
    @Test
    void conflictingSelectionNeverLeavesAPartialLock() throws Exception {
        try (var redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
             var claim = getClass().getResourceAsStream("/redis/claim-seats.lua");
             var release = getClass().getResourceAsStream("/redis/release-seats.lua")) {
            redis.start();
            String script = new String(claim.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("1", redis.execInContainer("redis-cli", "EVAL", script, "1", "B", "owner", "600000").getStdout().trim());
            assertEquals("0", redis.execInContainer("redis-cli", "EVAL", script, "2", "A", "B", "other", "600000").getStdout().trim());
            assertEquals("0", redis.execInContainer("redis-cli", "EXISTS", "A").getStdout().trim());
            redis.execInContainer("redis-cli", "EVAL", new String(release.readAllBytes(), StandardCharsets.UTF_8), "1", "B", "other");
            assertEquals("owner", redis.execInContainer("redis-cli", "GET", "B").getStdout().trim());
        }
    }
}

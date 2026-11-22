package br.pucrs.pulsepass.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Import(br.pucrs.pulsepass.messaging.KafkaRetries.class)
@SpringBootApplication
public class RealtimeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}

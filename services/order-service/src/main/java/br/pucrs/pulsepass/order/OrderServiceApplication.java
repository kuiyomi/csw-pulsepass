package br.pucrs.pulsepass.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.context.annotation.Import({br.pucrs.pulsepass.messaging.DomainEvents.class, br.pucrs.pulsepass.messaging.KafkaRetries.class})
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

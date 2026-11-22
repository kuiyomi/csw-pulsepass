package br.pucrs.pulsepass.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaRetries {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> producer) {
        var backoff = new ExponentialBackOffWithMaxRetries(3);
        backoff.setInitialInterval(1000);
        backoff.setMultiplier(2);
        var recoverer = new DeadLetterPublishingRecoverer(producer);
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, backoff);
    }
}

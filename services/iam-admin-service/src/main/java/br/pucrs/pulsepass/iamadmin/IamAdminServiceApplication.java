package br.pucrs.pulsepass.iamadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.context.annotation.Import(br.pucrs.pulsepass.messaging.DomainEvents.class)
@SpringBootApplication
public class IamAdminServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IamAdminServiceApplication.class, args);
    }
}

package br.pucrs.pulsepass.inventory.security;

import br.pucrs.pulsepass.security.RealmTokens;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class ServiceSecurity {
    @Bean
    SecurityFilterChain routes(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/events", "/events/*").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(RealmTokens.converter())))
                .build();
    }
}

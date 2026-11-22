package br.pucrs.pulsepass.apigateway;

import br.pucrs.pulsepass.security.RealmTokens;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Configuration
@RestController
public class GatewaySecurity {
    @Bean
    SecurityWebFilterChain routes(ServerHttpSecurity http, ReactiveStringRedisTemplate redis) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(a -> a.pathMatchers(HttpMethod.GET, "/api/inventory/events", "/api/inventory/events/*", "/actuator/health").permitAll()
                        .pathMatchers("/api/inventory/reservations/**").denyAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(r -> r.bearerTokenConverter(exchange -> {
                    var resolver = new org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter();
                    resolver.setAllowUriQueryParameter("/ws".equals(exchange.getRequest().getPath().value()));
                    return resolver.convert(exchange);
                }).jwt(j -> j.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(RealmTokens.converter()))))
                .addFilterAfter((exchange, chain) -> org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                        .map(context -> context.getAuthentication()).ofType(JwtAuthenticationToken.class)
                        .flatMap(token -> redis.hasKey("revoked:" + token.getToken().getId()))
                        .defaultIfEmpty(false).flatMap(revoked -> {
                            if (!revoked) return chain.filter(exchange);
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }), SecurityWebFiltersOrder.AUTHENTICATION).build();
    }

    @PostMapping("/api/session/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        Duration remaining = Duration.between(Instant.now(), jwt.getExpiresAt());
        return remaining.isPositive() ? redis.opsForValue().set("revoked:" + jwt.getId(), "1", remaining).then() : Mono.empty();
    }

    private final ReactiveStringRedisTemplate redis;
    public GatewaySecurity(ReactiveStringRedisTemplate redis) { this.redis = redis; }
}

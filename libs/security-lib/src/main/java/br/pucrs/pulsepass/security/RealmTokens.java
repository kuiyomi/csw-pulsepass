package br.pucrs.pulsepass.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

public final class RealmTokens {
    private RealmTokens() {}

    public static JwtAuthenticationConverter converter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> roles(jwt).stream()
                .map(role -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList());
        return converter;
    }

    public static List<String> roles(Jwt jwt) {
        if (jwt.getClaim("realm_access") instanceof Map<?, ?> realm
                && realm.get("roles") instanceof Collection<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}

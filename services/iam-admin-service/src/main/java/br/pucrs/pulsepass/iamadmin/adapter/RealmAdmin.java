package br.pucrs.pulsepass.iamadmin.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RealmAdmin {
    private final RestClient keycloak;
    private final String secret;
    public RealmAdmin(@Value("${keycloak.base-url:http://keycloak:8080}") String url,@Value("${IAM_CLIENT_SECRET}") String secret) {
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(5000);
        keycloak=RestClient.builder().baseUrl(url).requestFactory(factory).build();this.secret=secret;
    }
    public void grantOrganizer(UUID user) {
        var form=new LinkedMultiValueMap<String,String>();
        form.add("grant_type","client_credentials");form.add("client_id","iam-governance");form.add("client_secret",secret);
        try {
            var token=keycloak.post().uri("/realms/pulsepass/protocol/openid-connect/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class).required("access_token").asText();
            var role=keycloak.get().uri("/admin/realms/pulsepass/roles/ORGANIZADOR").headers(h->h.setBearerAuth(token))
                    .retrieve().body(JsonNode.class);
            keycloak.post().uri("/admin/realms/pulsepass/users/{id}/role-mappings/realm",user)
                    .headers(h->h.setBearerAuth(token)).body(List.of(role)).retrieve().toBodilessEntity();
        } catch(RestClientException ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Falha ao atribuir papel no Keycloak.",ex);}
    }
}

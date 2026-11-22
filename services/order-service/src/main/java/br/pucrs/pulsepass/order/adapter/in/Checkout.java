package br.pucrs.pulsepass.order.adapter.in;

import br.pucrs.pulsepass.messaging.DomainEvents;
import br.pucrs.pulsepass.messaging.ProcessedMessages;
import br.pucrs.pulsepass.order.domain.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
public class Checkout {
    public record Selection(UUID eventoId,List<UUID> assentoIds){}
    public record Payment(String metodo,String numeroCartao){}
    private final JdbcTemplate db;
    private final DomainEvents events;
    private final ObjectMapper json;
    private final RestClient inventory;
    private final int latency;
    private final double failureRate;
    private final AtomicInteger failures=new AtomicInteger();
    private final AtomicLong retryAt=new AtomicLong();

    public Checkout(JdbcTemplate db,DomainEvents events,ObjectMapper json,
                    @Value("${inventory.url:http://inventory-service:8080}") String url,
                    @Value("${payment.simulator.latency-ms:300}") int latency,
                    @Value("${payment.simulator.failure-rate:0}") double failureRate) {
        this.db=db;this.events=events;this.json=json;this.latency=latency;this.failureRate=failureRate;
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);factory.setReadTimeout(3000);
        inventory=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    private String encode(Object value) {
        try {return json.writeValueAsString(value);} catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private JsonNode decode(String value) {
        try {return json.readTree(value);} catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private String hash(Object body) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(body).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private record Attempt(UUID user,String op,UUID key,UUID order,ResponseEntity<?> replay){}
    private Attempt begin(UUID user,String op,UUID key,Object body,UUID order) {
        String digest=hash(body);
        // Stable orderId also survives a lost HTTP response followed by a database rollback.
        boolean locked=Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))",
                Boolean.class,user+"/"+op+"/"+key));
        if(!locked) return new Attempt(user,op,key,order,ResponseEntity.status(409).header("Retry-After","1").body(problem(409,"Tentativa em processamento.")));
        db.update("""
                INSERT INTO http_request(user_id,operation,request_key,order_id,body_hash) VALUES(?,?,?,?,?)
                ON CONFLICT DO NOTHING
                """,user,op,key,order,digest);
        var r=db.queryForMap("SELECT body_hash,response_status,response_body::text FROM http_request WHERE user_id=? AND operation=? AND request_key=?",user,op,key);
        if(!r.get("body_hash").equals(digest)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Chave usada com dados diferentes.");
        Integer status=(Integer)r.get("response_status");
        return new Attempt(user,op,key,order,status==null?null:response(status,decode((String)r.get("response_body")),true));
    }
    private Map<String,Object> problem(int status,String detail){return Map.of("status",status,"title",HttpStatus.valueOf(status).getReasonPhrase(),"detail",detail);}
    private ResponseEntity<?> response(int status,Object body,boolean replay) {
        var builder=ResponseEntity.status(status).contentType(status>=400?MediaType.APPLICATION_PROBLEM_JSON:MediaType.APPLICATION_JSON);
        if(replay) builder.header("Idempotency-Replayed","true");
        return builder.body(body);
    }
    private ResponseEntity<?> finish(Attempt a,int status,Object body) {
        db.update("UPDATE http_request SET response_status=?,response_body=?::jsonb WHERE user_id=? AND operation=? AND request_key=?",
                status,encode(body),a.user(),a.op(),a.key());
        return response(status,body,false);
    }
    private JsonNode call(String path,Object body,Jwt jwt) {
        if(System.currentTimeMillis()<retryAt.get()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Inventário temporariamente indisponível.");
        try {
            var request=inventory.post().uri(path).headers(h->h.setBearerAuth(jwt.getTokenValue()));
            if(body!=null) request.body(body);
            JsonNode result=request.retrieve().body(JsonNode.class);
            failures.set(0);return result;
        } catch(RestClientException ex) {
            if(ex instanceof HttpClientErrorException client){failures.set(0);throw client;}
            if(failures.incrementAndGet()>=3){retryAt.set(System.currentTimeMillis()+5000);failures.set(0);}
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Inventário indisponível; repita a tentativa.",ex);
        }
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") UUID key,@RequestBody Selection selection) {
        if(selection.eventoId()==null||selection.assentoIds()==null||selection.assentoIds().isEmpty()||selection.assentoIds().size()>8 ||
                selection.assentoIds().stream().anyMatch(Objects::isNull)||new HashSet<>(selection.assentoIds()).size()!=selection.assentoIds().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecione de um a oito assentos distintos.");
        UUID user=UUID.fromString(jwt.getSubject());
        UUID id=UUID.nameUUIDFromBytes((user+"/orders/"+key).getBytes(StandardCharsets.UTF_8));
        var attempt=begin(user,"create",key,selection,id);
        if(attempt.replay()!=null)return attempt.replay();
        lockOrder(id);
        db.update("INSERT INTO purchase_order(id,user_id,event_id,status) VALUES(?,?,?,'CRIADO')",id,user,selection.eventoId());
        JsonNode reservation;
        try {
            reservation=call("/reservations",Map.of("reservationId",id,"usuarioId",user,"eventoId",selection.eventoId(),"assentoIds",selection.assentoIds()),jwt);
        } catch(HttpClientErrorException ex) {
            db.update("UPDATE purchase_order SET status='EXPIRADO' WHERE id=?",id);
            int status=ex.getStatusCode().value();
            return finish(attempt,status,Map.of("status",status,"pedidoId",id,"detail","Não foi possível reservar os assentos."));
        }
        var prices=new ArrayList<Long>();
        var items=reservation.required("assentos");items.forEach(i->prices.add(i.required("preco").longValue()));
        var price=new OrderPrice(prices);
        var expiry=Instant.parse(reservation.required("expiraEm").asText());
        db.update("UPDATE purchase_order SET status=?,items=?::jsonb,expires_at=?,subtotal=?,fee=?,total=? WHERE id=?",
                OrderStatus.CRIADO.transitionTo(OrderStatus.RESERVADO).name(),items.toString(),Timestamp.from(expiry),price.subtotal(),price.fee(),price.total(),id);
        events.record("order.created",id,Map.of("pedidoId",id,"usuarioId",user,"eventoId",selection.eventoId(),"total",price.total()));
        return finish(attempt,201,view(id));
    }

    private Map<String,Object> view(UUID id) {
        var r=db.queryForMap("""
                SELECT id,status,expires_at AS "expiraEm",paid_at AS "pagoEm",items::text AS itens,subtotal,fee AS taxa,total
                FROM purchase_order WHERE id=?
                """,id);
        r.put("itens",decode((String)r.get("itens")));return r;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
    public Map<String,Object> get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        var owners=db.queryForList("SELECT user_id FROM purchase_order WHERE id=?",UUID.class,id);
        if(owners.isEmpty() || (!owners.getFirst().toString().equals(jwt.getSubject()) &&
                !br.pucrs.pulsepass.security.RealmTokens.roles(jwt).contains("ADMIN"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return view(id);
    }

    @PostMapping("/{id}/pay")
    @Transactional
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<?> pay(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") UUID key,@RequestBody Payment payment) {
        if(!"CARTAO_SIMULADO".equals(payment.metodo()) || payment.numeroCartao()==null || !payment.numeroCartao().matches("[0-9]{13,19}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cartão simulado inválido.");
        UUID user=UUID.fromString(jwt.getSubject());
        var attempt=begin(user,"pay/"+id,key,payment,id);
        if(attempt.replay()!=null)return attempt.replay();
        var rows=db.queryForList("SELECT event_id,status,expires_at,total,items::text FROM purchase_order WHERE id=? AND user_id=? FOR UPDATE",id,user);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var row=rows.getFirst();
        if(!"RESERVADO".equals(row.get("status"))) return finish(attempt,409,problem(409,"Pedido não está reservado."));
        db.update("UPDATE purchase_order SET status='AGUARDANDO_PAGAMENTO' WHERE id=?",id);
        try {Thread.sleep(latency);} catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
        String suffix=payment.numeroCartao().substring(payment.numeroCartao().length()-4);
        if(suffix.equals("0004") || suffix.equals("0002") || Math.random()<failureRate) {
            int status=suffix.equals("0004")?504:402;
            db.update("UPDATE purchase_order SET status='RESERVADO' WHERE id=?",id);
            return finish(attempt,status,problem(status,status==504?"Tempo de pagamento esgotado.":"Pagamento recusado."));
        }
        try {call("/reservations/"+id+"/confirm",null,jwt);}
        catch(HttpClientErrorException ex){
            if(ex.getStatusCode().value()!=409)throw ex;
            expire(id,user,(UUID)row.get("event_id"));return finish(attempt,409,problem(409,"Reserva expirada."));
        }
        Instant now=Instant.now();
        db.update("UPDATE purchase_order SET status='PAGO',paid_at=?,card_suffix=? WHERE id=?",Timestamp.from(now),suffix,id);
        events.record("order.paid",id,Map.of("pedidoId",id,"usuarioId",user,"eventoId",row.get("event_id"),"total",row.get("total"),
                "itens",decode((String)row.get("items")),"pagoEm",now.toString()));
        return finish(attempt,200,Map.of("id",id,"status","PAGO","pagoEm",now.toString()));
    }
    private void lockOrder(UUID id) {
        db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,1))", rs -> {}, id.toString());
    }
    private void expire(UUID id,UUID user,UUID event) {
        if(db.update("UPDATE purchase_order SET status='EXPIRADO' WHERE id=? AND status IN ('RESERVADO','AGUARDANDO_PAGAMENTO')",id)==1)
            events.record("order.expired",id,Map.of("pedidoId",id,"usuarioId",user,"eventoId",event));
    }
    @KafkaListener(topics="inventory.reservation-expired",groupId="order-service")
    @Transactional
    public void onExpiration(String message) {
        var envelope=decode(message);
        var id=UUID.fromString(envelope.required("aggregateId").asText());
        lockOrder(id);
        db.execute((ConnectionCallback<Boolean>) c-> new ProcessedMessages().applyOnce(c,"order-service",
                UUID.fromString(envelope.required("eventId").asText()),tx->{
                    var rows=db.queryForList("SELECT user_id,event_id FROM purchase_order WHERE id=? FOR UPDATE",id);
                    if(!rows.isEmpty()) expire(id,(UUID)rows.getFirst().get("user_id"),(UUID)rows.getFirst().get("event_id"));
                }));
    }
}

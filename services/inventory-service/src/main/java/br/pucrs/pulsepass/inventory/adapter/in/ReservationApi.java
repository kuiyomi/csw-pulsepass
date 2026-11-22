package br.pucrs.pulsepass.inventory.adapter.in;

import br.pucrs.pulsepass.inventory.adapter.out.SeatAllocationRepository;
import br.pucrs.pulsepass.messaging.DomainEvents;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReservationApi {
    private final JdbcTemplate db;
    private final DomainEvents events;
    private final Duration ttl;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private static org.springframework.data.redis.core.script.DefaultRedisScript<Long> script(String name) {
        var result = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>();
        result.setLocation(new org.springframework.core.io.ClassPathResource("redis/" + name + "-seats.lua"));
        result.setResultType(Long.class);
        return result;
    }
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> CLAIM = script("claim"), RELEASE = script("release");
    private void releaseAfter(UUID owner, List<String> keys, boolean committed) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                public void afterCompletion(int state) {
                    if ((committed && state == STATUS_COMMITTED) || (!committed && state == STATUS_ROLLED_BACK))
                        redis.execute(RELEASE, keys, owner.toString());
                }
            });
    }
    private List<String> seatKeys(List<UUID> ids) {
        return ids.stream().map(id -> "seat:" + id).toList();
    }
    private final SeatAllocationRepository seats = new SeatAllocationRepository();
    public ReservationApi(JdbcTemplate db,DomainEvents events,org.springframework.data.redis.core.StringRedisTemplate redis,@Value("${inventory.reservation.ttl:PT10M}") Duration ttl) {
        this.db=db; this.events=events; this.ttl=ttl; this.redis=redis;
    }
    public record Request(UUID reservationId,UUID eventoId,UUID usuarioId,List<UUID> assentoIds){}
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/reservations")
    @Transactional
    public ResponseEntity<?> reserve(@AuthenticationPrincipal Jwt jwt,@RequestBody Request r) {
        if(r.reservationId()==null || r.eventoId()==null || r.assentoIds()==null || r.assentoIds().isEmpty() ||
                r.assentoIds().size()>8 || r.assentoIds().contains(null) || new HashSet<>(r.assentoIds()).size()!=r.assentoIds().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(!jwt.getSubject().equals(String.valueOf(r.usuarioId()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        int inserted=db.update("""
                INSERT INTO reservation(id,event_id,user_id,status,created_at,expires_at)
                SELECT ?,id,?,'ATIVA',now(),now() + ? * interval '1 millisecond' FROM venue_event
                WHERE id=? AND status='PUBLICADO' ON CONFLICT(id) DO NOTHING
                """,r.reservationId(),r.usuarioId(),ttl.toMillis(),r.eventoId());
        var rows=db.queryForList("SELECT status,expires_at,expires_at>clock_timestamp() AS valid FROM reservation WHERE id=? AND user_id=? AND event_id=? FOR UPDATE",
                r.reservationId(),r.usuarioId(),r.eventoId());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!Boolean.TRUE.equals(rows.getFirst().get("valid")) || !rows.getFirst().get("status").equals("ATIVA")) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if(inserted==1) {
            var keys = seatKeys(r.assentoIds());
            releaseAfter(r.reservationId(),keys,false);
            if (!Long.valueOf(1).equals(redis.execute(CLAIM,keys,r.reservationId().toString(),Long.toString(ttl.toMillis()))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Um dos assentos já está reservado.");
            db.execute((ConnectionCallback<Void>) c->{seats.allocate(c,r.eventoId(),r.reservationId(),r.assentoIds());return null;});
        }
        var items=db.queryForList("""
                SELECT s.id AS "assentoId",sec.id AS "setorId",sec.name AS "setorNome",sec.price AS preco
                FROM seat s JOIN sector sec ON sec.id=s.sector_id WHERE reservation_id=? ORDER BY s.id
                """,r.reservationId());
        if(!new HashSet<>(r.assentoIds()).equals(new HashSet<>(items.stream().map(m->m.get("assentoId")).toList())))
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        var expiry=((Timestamp)rows.getFirst().get("expires_at")).toInstant().toString();
        if(inserted==1) events.record("inventory.seats-reserved",r.reservationId(),Map.of("reservaId",r.reservationId(),"pedidoId",r.reservationId(),
                "eventoId",r.eventoId(),"usuarioId",r.usuarioId(),"assentos",items,"expiraEm",expiry));
        return ResponseEntity.status(inserted==1?201:200).body(Map.of("reservationId",r.reservationId(),"expiraEm",expiry,"assentos",items));
    }

    @ExceptionHandler(SeatAllocationRepository.SeatConflictException.class)
    public ProblemDetail conflict(SeatAllocationRepository.SeatConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,ex.getMessage());
    }

    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/reservations/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void confirm(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        var result=db.queryForList("SELECT status FROM reservation WHERE id=? AND user_id=? FOR UPDATE",String.class,id,UUID.fromString(jwt.getSubject()));
        if(result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(result.getFirst().equals("CONFIRMADA")) return;
        if(!Boolean.TRUE.equals(db.execute((ConnectionCallback<Boolean>) c->seats.confirm(c,id)))) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @DeleteMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLIENTE')")
    @Transactional
    public void cancel(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        var rows = db.queryForList("SELECT event_id FROM reservation WHERE id=? AND user_id=? AND status='ATIVA' FOR UPDATE",
                UUID.class,id,UUID.fromString(jwt.getSubject()));
        if (rows.isEmpty()) return;
        var ids = db.queryForList("SELECT id FROM seat WHERE reservation_id=?", UUID.class,id);
        if (Boolean.TRUE.equals(db.execute((ConnectionCallback<Boolean>) c->seats.cancel(c,id)))) {
            releaseAfter(id,seatKeys(ids),true);
            events.record("inventory.seats-released",id,Map.of("reservaId",id,"eventoId",rows.getFirst(),"assentoIds",ids,"motivo","CANCELAMENTO"));
        }
    }
    @Scheduled(fixedDelayString="${inventory.sweep-ms:1000}")
    @Transactional
    public void expire() {
        var due=db.queryForList("SELECT id,event_id FROM reservation WHERE status='ATIVA' AND expires_at<=now() ORDER BY expires_at LIMIT 50 FOR UPDATE SKIP LOCKED");
        for(var row:due) {
            UUID id=(UUID)row.get("id");
            var ids=db.queryForList("SELECT id FROM seat WHERE reservation_id=?",UUID.class,id);
            if(Boolean.TRUE.equals(db.execute((ConnectionCallback<Boolean>) c->seats.expire(c,id)))) {
                releaseAfter(id,seatKeys(ids),true);
                events.record("inventory.reservation-expired",id,Map.of("reservaId",id,"pedidoId",id,"eventoId",row.get("event_id"),"assentoIds",ids));
                events.record("inventory.seats-released",id,Map.of("reservaId",id,"eventoId",row.get("event_id"),"assentoIds",ids,"motivo","EXPIRACAO"));
            }
        }
    }
}

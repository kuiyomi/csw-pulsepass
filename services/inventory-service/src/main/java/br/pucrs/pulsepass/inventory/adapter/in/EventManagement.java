package br.pucrs.pulsepass.inventory.adapter.in;

import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class EventManagement {
    private final JdbcTemplate db;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    public EventManagement(JdbcTemplate db, org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.db = db; this.redis = redis;
    }

    public record NewEvent(String nome, String descricao, Instant dataHora, String local) {}
    public record NewSector(String nome, long preco, int fileiras, int assentosPorFileira, boolean acessivel) {}
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@AuthenticationPrincipal Jwt jwt, @RequestBody NewEvent e) {
        if (e.nome()==null || e.nome().isBlank() || e.local()==null || e.local().isBlank() ||
                e.dataHora()==null || e.descricao()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var id = UUID.randomUUID();
        db.update("INSERT INTO venue_event(id,organizer_id,name,description,starts_at,venue,status) VALUES(?,?,?,?,?,?,'RASCUNHO')",
                id,user(jwt),e.nome().strip(),e.descricao(),java.sql.Timestamp.from(e.dataHora()),e.local().strip());
        return Map.of("id",id,"status","RASCUNHO");
    }

    private void lockDraft(UUID id, Jwt jwt) {
        var states=db.queryForList("SELECT status FROM venue_event WHERE id=? AND organizer_id=? FOR UPDATE",String.class,id,user(jwt));
        if(states.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!states.getFirst().equals("RASCUNHO")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Evento já publicado.");
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @PostMapping("/events/{id}/sectors")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> sector(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestBody NewSector s) {
        lockDraft(id,jwt);
        if(s.nome()==null || s.nome().isBlank() || s.preco()<=0 || s.fileiras()<1 || s.fileiras()>50 ||
                s.assentosPorFileira()<1 || s.assentosPorFileira()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        UUID sector=UUID.randomUUID();
        db.update("INSERT INTO sector VALUES(?,?,?,?,?)",sector,id,s.nome(),s.preco(),s.acessivel());
        int count=db.update("""
                INSERT INTO seat(id,sector_id,row_label,seat_number,status)
                SELECT gen_random_uuid(),?,CASE WHEN r<=26 THEN chr(64+r) ELSE 'A'||chr(64+r-26) END,n,'LIVRE'
                FROM generate_series(1,?) r CROSS JOIN generate_series(1,?) n
                """,sector,s.fileiras(),s.assentosPorFileira());
        return Map.of("setorId",sector,"assentosCriados",count);
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @PostMapping("/events/{id}/publish")
    @Transactional
    public Map<String,String> publish(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        lockDraft(id,jwt);
        if(db.update("UPDATE venue_event SET status='PUBLICADO' WHERE id=? AND EXISTS (SELECT 1 FROM sector WHERE event_id=?)",id,id)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Cadastre um setor antes de publicar.");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    public void afterCommit() { redis.opsForValue().increment("catalog:generation"); }
                });
        return Map.of("status","PUBLICADO");
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @GetMapping("/organizer/events")
    public List<Map<String,Object>> own(@AuthenticationPrincipal Jwt jwt) {
        return db.queryForList("""
                SELECT e.id,e.name AS nome,e.status,count(s.id) FILTER(WHERE s.status='VENDIDO') AS vendidos,
                       count(s.id) FILTER(WHERE s.status='RESERVADO') AS reservados
                FROM venue_event e LEFT JOIN sector sec ON sec.event_id=e.id LEFT JOIN seat s ON s.sector_id=sec.id
                WHERE e.organizer_id=? GROUP BY e.id ORDER BY e.starts_at
                """,user(jwt));
    }

    @PreAuthorize("hasRole('CLIENTE')")
    @GetMapping("/events/{id}/seats")
    public Map<String,Object> seats(@PathVariable UUID id) {
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM venue_event WHERE id=? AND status='PUBLICADO')",Boolean.class,id)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var sectors=db.queryForList("SELECT id AS \"setorId\",name AS nome,price AS preco FROM sector WHERE event_id=? ORDER BY name,id",id);
        for(var sector:sectors) sector.put("assentos",db.queryForList("""
                SELECT id,row_label AS fileira,seat_number AS numero,status FROM seat WHERE sector_id=?
                ORDER BY length(row_label),row_label,seat_number
                """,sector.get("setorId")));
        return Map.of("eventoId",id,"setores",sectors);
    }
}

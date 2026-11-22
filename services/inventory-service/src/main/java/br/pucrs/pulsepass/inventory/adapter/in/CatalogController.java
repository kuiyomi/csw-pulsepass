package br.pucrs.pulsepass.inventory.adapter.in;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("catalog")
@RequestMapping("/events")
public class CatalogController {
    private final DataSource dataSource;

    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public CatalogController(DataSource dataSource, org.springframework.data.redis.core.StringRedisTemplate redis,
                             com.fasterxml.jackson.databind.ObjectMapper json) {
        this.dataSource = dataSource; this.redis=redis; this.json=json;
    }

    public record Summary(UUID id, String nome, java.time.Instant dataHora, String local,
                          String status, long precoMinimo, String disponibilidade) {}
    public record Page(List<Summary> itens, int pagina, long total) {}
    public record Sector(UUID id, String nome, long preco, long capacidade, long livres, boolean acessivel) {}
    public record Detail(UUID id, String nome, String descricao, java.time.Instant dataHora,
                         String local, List<Sector> setores) {}

    @GetMapping
    public Page list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size) throws SQLException {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
        }
        String generation = redis.opsForValue().get("catalog:generation");
        String key = "catalog:list:" + generation + ":" + page + ":" + size + ":" +
                UUID.nameUUIDFromBytes(q.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String cached = redis.opsForValue().get(key);
        try {
            if (cached != null) return json.readValue(cached, Page.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { redis.delete(key); }
        String filter = " WHERE e.status = 'PUBLICADO' AND strpos(lower(e.name), lower(?)) > 0 ";
        try (var db = dataSource.getConnection();
             var count = db.prepareStatement("SELECT count(*) FROM venue_event e" + filter);
             var query = db.prepareStatement("""
                     SELECT e.id, e.name, e.starts_at, e.venue, min(sec.price),
                            coalesce(bool_or(s.status = 'LIVRE'),false)
                     FROM venue_event e JOIN sector sec ON sec.event_id = e.id
                     LEFT JOIN seat s ON s.sector_id = sec.id
                     """ + filter + " GROUP BY e.id ORDER BY e.starts_at, e.id LIMIT ? OFFSET ?")) {
            count.setString(1, q);
            query.setString(1, q);
            query.setInt(2, size);
            query.setLong(3, (long) page * size);
            var items = new ArrayList<Summary>();
            try (var rows = query.executeQuery(); var totals = count.executeQuery()) {
                while (rows.next()) {
                    items.add(new Summary(rows.getObject(1, UUID.class), rows.getString(2),
                            rows.getTimestamp(3).toInstant(), rows.getString(4), "PUBLICADO",
                            rows.getLong(5), rows.getBoolean(6) ? "ABERTA" : "ESGOTADO"));
                }
                totals.next();
                var result = new Page(items, page, totals.getLong(1));
                try { redis.opsForValue().set(key,json.writeValueAsString(result),java.time.Duration.ofSeconds(30)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
                return result;
            }
        }
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable UUID id) throws SQLException {
        try (var db = dataSource.getConnection();
             var event = db.prepareStatement("SELECT name, description, starts_at, venue FROM venue_event WHERE id = ? AND status = 'PUBLICADO'");
             var sectors = db.prepareStatement("""
                     SELECT sec.id, sec.name, sec.price, count(s.id), count(s.id) FILTER (WHERE s.status='LIVRE'), sec.accessible
                     FROM sector sec LEFT JOIN seat s ON s.sector_id=sec.id
                     WHERE sec.event_id=? GROUP BY sec.id ORDER BY sec.name, sec.id
                     """)) {
            event.setObject(1, id);
            sectors.setObject(1, id);
            try (var rows = event.executeQuery()) {
                if (!rows.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                var items = new ArrayList<Sector>();
                try (var result = sectors.executeQuery()) {
                    while (result.next()) {
                        items.add(new Sector(result.getObject(1, UUID.class), result.getString(2), result.getLong(3),
                                result.getLong(4), result.getLong(5), result.getBoolean(6)));
                    }
                }
                return new Detail(id, rows.getString(1), rows.getString(2), rows.getTimestamp(3).toInstant(),
                        rows.getString(4), items);
            }
        }
    }
}

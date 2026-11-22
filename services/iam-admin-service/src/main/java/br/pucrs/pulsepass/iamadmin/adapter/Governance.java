package br.pucrs.pulsepass.iamadmin.adapter;

import br.pucrs.pulsepass.iamadmin.domain.OrganizerRequestStatus;
import br.pucrs.pulsepass.messaging.DomainEvents;
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
@RequestMapping("/organizer-requests")
public class Governance {
    private final JdbcTemplate db;
    private final RealmAdmin realm;
    private final DomainEvents events;
    public Governance(JdbcTemplate db,RealmAdmin realm,DomainEvents events){this.db=db;this.realm=realm;this.events=events;}
    public record Apply(String nomeFantasia,String documento,String justificativa){}
    public record Rejection(String motivo){}
    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> apply(@RequestBody Apply a,@AuthenticationPrincipal Jwt jwt){
        if(a.nomeFantasia()==null||a.nomeFantasia().strip().length()<3||a.nomeFantasia().length()>80 ||
                a.documento()==null||a.documento().length()<11||a.documento().length()>18 ||
                a.justificativa()==null||a.justificativa().length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        UUID id=UUID.randomUUID(),user=UUID.fromString(jwt.getSubject());
        int inserted=db.update("INSERT INTO organizer_request(id,user_id,company,document,justification,status) VALUES(?,?,?,?,?,'PENDENTE') ON CONFLICT DO NOTHING",
                id,user,a.nomeFantasia(),a.documento(),a.justificativa());
        if(inserted==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Solicitação existente.");
        events.record("iam.organizer-requested",id,Map.of("solicitacaoId",id,"usuarioId",user));
        return db.queryForMap("SELECT id,status,created_at AS \"criadaEm\" FROM organizer_request WHERE id=?",id);
    }
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> list(@RequestParam(defaultValue="PENDENTE") OrganizerRequestStatus status,
                                   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var items=db.queryForList("""
                SELECT id,user_id AS "usuarioId",company AS "nomeFantasia",document AS documento,justification AS justificativa,
                status,created_at AS "criadaEm" FROM organizer_request WHERE status=? ORDER BY created_at,id LIMIT ? OFFSET ?
                """,status.name(),size,(long)page*size);
        return Map.of("itens",items,"pagina",page,"total",db.queryForObject("SELECT count(*) FROM organizer_request WHERE status=?",Long.class,status.name()));
    }
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String,Object> approve(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return decide(id,jwt,null,true);}
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String,Object> reject(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestBody Rejection r){return decide(id,jwt,r.motivo(),false);}
    private Map<String,Object> decide(UUID id,Jwt jwt,String reason,boolean approve){
        var rows=db.queryForList("SELECT user_id,status FROM organizer_request WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        UUID user=(UUID)rows.getFirst().get("user_id"),actor=UUID.fromString(jwt.getSubject());
        var state=OrganizerRequestStatus.valueOf((String)rows.getFirst().get("status"));
        OrganizerRequestStatus next;
        try{next=approve?state.approve():state.reject(reason);}
        catch(IllegalStateException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage());}
        catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());}
        if(state!=next){
            if(approve)realm.grantOrganizer(user);
            db.update("UPDATE organizer_request SET status=?,decided_by=?,decided_at=now(),reason=? WHERE id=?",next.name(),actor,reason,id);
            db.update("INSERT INTO organizer_audit(request_id,actor,action,reason) VALUES(?,?,?,?)",id,actor,next.name(),reason);
            if(approve)events.record("iam.organizer-approved",id,Map.of("solicitacaoId",id,"usuarioId",user,"decididoPor",actor));
        }
        return db.queryForMap("SELECT id,status,decided_by AS \"decididoPor\",decided_at AS \"decididoEm\" FROM organizer_request WHERE id=?",id);
    }
}

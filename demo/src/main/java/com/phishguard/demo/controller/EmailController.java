package com.phishguard.demo.controller;

import com.phishguard.demo.model.EmailSalvo;
import com.phishguard.demo.model.Usuario;
import com.phishguard.demo.repository.EmailSalvoRepository;
import com.phishguard.demo.service.GmailService;
import com.phishguard.demo.Orchestrator.PhishingOrchestrator;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.dto.AnalyseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/caixa")
public class EmailController {

    private final EmailSalvoRepository emailSalvoRepo;
    private final GmailService         gmailService;
    private final PhishingOrchestrator orchestrator;

    public EmailController(EmailSalvoRepository emailSalvoRepo,
                           GmailService gmailService,
                           PhishingOrchestrator orchestrator) {
        this.emailSalvoRepo = emailSalvoRepo;
        this.gmailService   = gmailService;
        this.orchestrator   = orchestrator;
    }

    private Usuario getUsuario() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario u) return u;
        return null;
    }

    // Sincroniza emails do Gmail e salva no banco
    @PostMapping("/sincronizar")
    public ResponseEntity<?> sincronizar() {
        try {
            Usuario usuario = getUsuario();
            if (usuario == null) return ResponseEntity.status(401)
                .body(Map.of("erro", "Não autenticado"));

            List<GmailDTO> emails = gmailService.buscarEmails(usuario);
            int novos = 0;

            for (GmailDTO gmail : emails) {
                String gmailId = gmail.getEmailId();
                if (gmailId != null && emailSalvoRepo.existsByUsuarioAndGmailId(usuario, gmailId)) {
                    continue; // já salvo
                }

                AnalyseDTO analise = orchestrator.analisarFluxoCompleto(gmail);

                EmailSalvo salvo = new EmailSalvo(
                    usuario, gmailId != null ? gmailId : UUID.randomUUID().toString(),
                    gmail.getFrom(), gmail.getSubject(), gmail.getBody(), "inbox"
                );
                salvo.setClassificacao(analise.getClassificacao());
                salvo.setScore(analise.getScore());
                salvo.setMotivos(analise.getMotivos());
                emailSalvoRepo.save(salvo);
                novos++;
            }

            return ResponseEntity.ok(Map.of("sincronizados", novos, "total", emails.size()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    // Lista emails por pasta
    @GetMapping("/pasta/{pasta}")
    public ResponseEntity<?> listarPorPasta(@PathVariable String pasta) {
        Usuario usuario = getUsuario();
        if (usuario == null) return ResponseEntity.status(401)
            .body(Map.of("erro", "Não autenticado"));

        List<EmailSalvo> emails = pasta.equals("favoritos")
            ? emailSalvoRepo.findByUsuarioAndFavoritoTrueOrderByRecebidoEmDesc(usuario)
            : emailSalvoRepo.findByUsuarioAndPastaOrderByRecebidoEmDesc(usuario, pasta);

        return ResponseEntity.ok(emails.stream().map(this::toMap).toList());
    }

    // Marca como lido
    @PatchMapping("/{id}/lido")
    @Transactional
    public ResponseEntity<?> marcarLido(@PathVariable Long id) {
        Usuario usuario = getUsuario();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        emailSalvoRepo.marcarComoLido(usuario, id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // Toggle favorito
    @PatchMapping("/{id}/favorito")
    @Transactional
    public ResponseEntity<?> toggleFavorito(@PathVariable Long id,
                                             @RequestBody Map<String, Boolean> body) {
        Usuario usuario = getUsuario();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        boolean favorito = Boolean.TRUE.equals(body.get("favorito"));
        emailSalvoRepo.atualizarFavorito(usuario, id, favorito);
        return ResponseEntity.ok(Map.of("status", "ok", "favorito", favorito));
    }

    // Mover para pasta (archive, trash, inbox)
    @PatchMapping("/{id}/pasta")
    @Transactional
    public ResponseEntity<?> moverPasta(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        Usuario usuario = getUsuario();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        String pasta = body.get("pasta");
        if (pasta == null) return ResponseEntity.badRequest().body(Map.of("erro", "Pasta obrigatória"));
        emailSalvoRepo.moverParaPasta(usuario, id, pasta);
        return ResponseEntity.ok(Map.of("status", "ok", "pasta", pasta));
    }

    // Deletar
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        Usuario usuario = getUsuario();
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        emailSalvoRepo.findByUsuarioAndId(usuario, id).ifPresent(emailSalvoRepo::delete);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Map<String, Object> toMap(EmailSalvo e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             e.getId());
        m.put("gmailId",        e.getGmailId());
        m.put("from",           e.getRemetente());
        m.put("subject",        e.getAssunto());
        m.put("body",           e.getCorpo());
        m.put("pasta",          e.getPasta());
        m.put("lido",           e.isLido());
        m.put("favorito",       e.isFavorito());
        m.put("classificacao",  e.getClassificacao());
        m.put("score",          e.getScore());
        m.put("motivos",        e.getMotivos());
        m.put("recebidoEm",     e.getRecebidoEm().toString());
        return m;
    }
}

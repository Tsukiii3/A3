package com.phishguard.demo.controller;

import com.phishguard.demo.Orchestrator.PhishingOrchestrator;
import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.model.Usuario;
import com.phishguard.demo.repository.UsuarioRepository;
import com.phishguard.demo.service.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/emails")
public class GmailController {

    private final GmailService         gmailService;
    private final PhishingOrchestrator orchestrator;
    private final UsuarioRepository   usuarioRepo;

    public GmailController(GmailService gmailService,
                           PhishingOrchestrator orchestrator,
                           UsuarioRepository usuarioRepo) {
        this.gmailService = gmailService;
        this.orchestrator = orchestrator;
        this.usuarioRepo  = usuarioRepo;
    }

    @GetMapping("/analisar")
    public ResponseEntity<?> analisar() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
                return ResponseEntity.status(401)
                    .body(Map.of("erro", "Não autenticado"));
            }
            List<GmailDTO> emails;
            if (usuario.getGmailAccessToken() != null
                    && !usuario.getGmailAccessToken().isBlank()) {
                emails = gmailService.buscarEmails(usuario);
            } else {
                emails = gmailService.buscarEmails();
            }

            List<Map<String, Object>> resp = new ArrayList<>();
            for (GmailDTO email : emails) {
                AnalyseDTO r = orchestrator.analisarFluxoCompleto(email);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("from",          email.getFrom());
                item.put("subject",       email.getSubject());
                item.put("body",          email.getBody());
                item.put("classificacao", r.getClassificacao());
                item.put("score",         r.getScore());
                item.put("motivos",       r.getMotivos());
                resp.add(item);
            }

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                .body(Map.of("erro", e.getMessage()));
        }
        @PostMapping("/enviar")
public ResponseEntity<?> enviar(@RequestBody Map<String, String> body) {
    try {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        String para   = body.get("para");
        String assunto = body.get("assunto");
        String corpo  = body.get("corpo");

        if (para == null || assunto == null || corpo == null) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Campos obrigatórios ausentes"));
        }

        gmailService.enviarEmail(usuario, para, assunto, corpo);
        return ResponseEntity.ok(Map.of("status", "enviado"));

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
    }
}
    }
}
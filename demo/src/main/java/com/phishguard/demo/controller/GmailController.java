package com.phishguard.demo.controller;

import com.phishguard.demo.service.AiAnalyseService;
import com.phishguard.demo.service.GmailService;
import com.phishguard.demo.Orchestrator.PhishingOrchestrator;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.dto.AnalyseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/emails")
public class GmailController {

    private final GmailService gmailService;
    private final PhishingOrchestrator orchestrator;

    public GmailController(GmailService gmailService, PhishingOrchestrator orchestrator) {
        this.gmailService = gmailService;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/analisar")
    public ResponseEntity<?> analisarEmails() {
        try {
            List<GmailDTO> emails = gmailService.buscarEmails();
            List<Map<String, Object>> resposta = new ArrayList<>();

            for (GmailDTO email : emails) {
                // Se o orquestrador falhar para um email específico, o try/catch interno trata
                AnalyseDTO resultado = orchestrator.analisarFluxoCompleto(email);

                Map<String, Object> item = new LinkedHashMap<>(); // LinkedHashMap mantém a ordem
                item.put("from", email.getFrom());
                item.put("subject", email.getSubject());
                item.put("classificacao", resultado.getClassificacao());
                item.put("score", resultado.getScore());
                item.put("motivos", resultado.getMotivos());
                resposta.add(item);
            }
            return ResponseEntity.ok(resposta);
        } catch (Exception e) {
            // Isso vai mostrar o erro real (Flags ou 404) no console do seu VS Code/IntelliJ
            e.printStackTrace(); 
            return ResponseEntity.status(500).body("Erro interno no processamento: " + e.getLocalizedMessage());
        }
    }
}
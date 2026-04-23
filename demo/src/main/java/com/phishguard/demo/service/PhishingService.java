package com.phishguard.demo.service;

import org.springframework.stereotype.Service;
import java.util.*;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.util.LinkExtractor;

@Service
public class PhishingService {

    private final List<String> trustedDomains = List.of(
            "linkedin.com", "google.com", "bradesco.com.br", "nubank.com.br",
            "youtube.com", "amazon.com", "microsoft.com", "oracle.com",
            "ea.com", "github.com", "newsletter.artlist.io"
    );

    private final List<String> suspiciousUrlKeywords = List.of("login", "verify", "atualizar", "seguranca", "click");

    public AnalyseDTO analisar(GmailDTO email) {
        int score = 0;
        Set<String> motivos = new HashSet<>();

        String from = email.getFrom().toLowerCase();
        String body = email.getBody();
        String dominioEmail = from.contains("@") ? from.substring(from.indexOf("@") + 1).replace(">", "") : "";
        boolean isTrusted = trustedDomains.stream().anyMatch(dominioEmail::endsWith);

        // 1. Remetente Gratuito
        if (dominioEmail.endsWith("gmail.com") || dominioEmail.endsWith("hotmail.com")) {
            score += 15;
            motivos.add("Remetente utiliza serviço de e-mail gratuito/pessoal");
        }

        // 2. Links Externos (Reduzi o peso para evitar os 100 pontos imediatos)
        List<String> links = LinkExtractor.extrairLinks(body);
        for (String link : links) {
            if (!isTrusted) {
                score += 20; 
                motivos.add("Contém links para domínios externos");
                for (String kw : suspiciousUrlKeywords) {
                    if (link.toLowerCase().contains(kw)) {
                        score += 25;
                        motivos.add("URL com termo suspeito (" + kw + ")");
                    }
                }
            }
        }

        // 3. Gatilhos de Urgência
        if (body.toLowerCase().contains("urgente") || body.toLowerCase().contains("bloqueio")) {
            score += 20;
            motivos.add("Uso de táticas de urgência");
        }

        if (score > 90) score = 90; // Deixa margem para o Gemini ou SafeBrowsing decidirem

        return new AnalyseDTO("PROCESSANDO", score, new ArrayList<>(motivos));
    }
}
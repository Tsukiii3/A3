package com.phishguard.demo.Orchestrator;

import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.service.*;
import com.phishguard.demo.util.LinkExtractor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PhishingOrchestrator {

    private final PhishingService phishingService;
    private final SafeBrowsingService safeBrowsingService;
    private final AiAnalyseService aiAnalyseService;

    public PhishingOrchestrator(PhishingService p,
                                SafeBrowsingService s,
                                AiAnalyseService a) {
        this.phishingService = p;
        this.safeBrowsingService = s;
        this.aiAnalyseService = a;
    }

    public AnalyseDTO analisarFluxoCompleto(GmailDTO email) {

        // 1. Heurística base
        AnalyseDTO base = phishingService.analisar(email);
        int score = base.getScore();
        List<String> motivos = new ArrayList<>(base.getMotivos());

        // 2. SafeBrowsing
        List<String> links = LinkExtractor.extrairLinks(email.getBody());
        if (!links.isEmpty() && safeBrowsingService.isMalicious(links)) {
            score += 30;
            motivos.add("SafeBrowsing: link malicioso detectado");
        }

        score = Math.max(0, Math.min(score, 100));

        // 3. IA na zona de incerteza
        if (score >= 30 && score <= 80) {
            AnalyseDTO ia = aiAnalyseService.analisarComIA(email, score, motivos);
            score = (int) (score * 0.4 + ia.getScore() * 0.6);
            score = Math.max(0, Math.min(score, 100));
            motivos.addAll(ia.getMotivos());
        }

        // 4. Classificação final
        String classificacao = score < 30 ? "SEGURO"
                             : score < 60 ? "SUSPEITO"
                             : "FRAUDE";

        return new AnalyseDTO(classificacao, score, motivos);
    }

    @Deprecated
    public AnalyseDTO analisar(GmailDTO email) {
        return analisarFluxoCompleto(email);
    }
}
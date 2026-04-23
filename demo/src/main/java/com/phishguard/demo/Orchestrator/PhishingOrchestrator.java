package com.phishguard.demo.Orchestrator;

import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.service.*;
import com.phishguard.demo.util.LinkExtractor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PhishingOrchestrator {

    private final PhishingService phishingService;
    private final SafeBrowsingService safeBrowsingService;
    private final AiAnalyseService aiAnalyseService;

    public PhishingOrchestrator(PhishingService p, SafeBrowsingService s, AiAnalyseService a) {
        this.phishingService = p;
        this.safeBrowsingService = s;
        this.aiAnalyseService = a;
    }

    public AnalyseDTO analisarFluxoCompleto(GmailDTO email) {
    // 1º PORTÃO: Heurística sempre primeiro.
    AnalyseDTO resultado = phishingService.analisar(email);
    
    // Se a heurística diz que é SEGURO (score muito baixo), 
    // não gastamos tempo/dinheiro com SafeBrowsing ou IA.
    if (resultado.getScore() < 25) {
        resultado.setClassificacao("SEGURO");
        resultado.getMotivos().add("IA e SafeBrowsing não acionados por regras da heurística");
        return resultado;
    }

    // 2º PORTÃO: SafeBrowsing só se houver links E o score técnico já for suspeito.
    List<String> links = LinkExtractor.extrairLinks(email.getBody());
    if (!links.isEmpty() && resultado.getScore() >= 25) {
        if (safeBrowsingService.isMalicious(links)) {
            resultado.setScore(resultado.getScore() + 50);
            resultado.getMotivos().add("Google Safe Browsing: Link malicioso detectado");
        }
    }

    // 3º PORTÃO: IA (Corte Suprema) só se o score acumulado for de FRAUDE (>= 50).
    if (resultado.getScore() >= 50) {
        System.out.println("Score alto (" + resultado.getScore() + "). Acionando IA para veredito...");
        AnalyseDTO resultadoIA = aiAnalyseService.analisarComIA(email, resultado.getMotivos());

        if (resultadoIA != null) {
            return resultadoIA; // Retorna o veredito final da IA
        }
        
        // Se a IA falhar (null), mantém o que a heurística/safe encontrou
        resultado.setClassificacao("FRAUDE");
        resultado.getMotivos().add("Análise técnica concluiu fraude (IA Offline)");
    } else {
        // Se passou do 1º portão mas não chegou no 3º (score entre 25 e 49)
        resultado.setClassificacao("SUSPEITO");
    }

    return resultado;
}
}
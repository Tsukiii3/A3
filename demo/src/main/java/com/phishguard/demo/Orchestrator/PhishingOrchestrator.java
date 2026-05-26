package com.phishguard.demo.Orchestrator;

import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.model.EmailGolpistas;
import com.phishguard.demo.model.UrlPhishing;
import com.phishguard.demo.repository.EmailGolpistaRepository;
import com.phishguard.demo.repository.UrlPhishingRepository;
import com.phishguard.demo.service.*;
import com.phishguard.demo.util.LinkExtractor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
public class PhishingOrchestrator {

    private final PhishingService         phishingService;
    private final SafeBrowsingService     safeBrowsingService;
    private final AiAnalyseService        aiAnalyseService;
    private final EmailGolpistaRepository emailRepo;
    private final UrlPhishingRepository   urlRepo;

    // Domínios sempre confiáveis — nunca salvos como golpistas
    private static final Set<String> TRUSTED_SENDERS_WHITELIST = Set.of(
    // Social
    "linkedin.com", "facebook.com", "instagram.com",
    "twitter.com", "x.com", "youtube.com",
    // Tech
    "google.com", "gmail.com", "microsoft.com", "github.com",
    "apple.com", "discord.com", "slack.com", "zoom.us",
    // E-commerce
    "amazon.com", "amazon.com.br", "mercadolivre.com.br",
    "mercadopago.com.br", "shopee.com.br", "americanas.com.br",
    // Financeiro BR
    "nubank.com.br", "itau.com.br", "bradesco.com.br",
    "santander.com.br", "bb.com.br", "caixa.gov.br",
    "inter.co", "c6bank.com.br", "paypal.com",
    // Streaming
    "netflix.com", "spotify.com", "disneyplus.com",
    // Serviços BR
    "ifood.com.br", "uber.com", "99app.com",
    "correios.com.br", "gov.br",
    // Educação BR
    "animaeducacao.com.br", "kroton.com.br", "cogna.com.br",
    "anhanguera.com", "unopar.br",
    // CDNs e infraestrutura de email legítimos
    "licdn.com",    
    "sendgrid.net", "amazonses.com", "mailchimp.com",
    "klaviyo.com", "mailgun.org"
);

    public PhishingOrchestrator(PhishingService p, SafeBrowsingService s,
                                AiAnalyseService a,
                                EmailGolpistaRepository emailRepo,
                                UrlPhishingRepository urlRepo) {
        this.phishingService     = p;
        this.safeBrowsingService = s;
        this.aiAnalyseService    = a;
        this.emailRepo           = emailRepo;
        this.urlRepo             = urlRepo;
    }

    public AnalyseDTO analisarFluxoCompleto(GmailDTO email) {
        String remetente = email.getFrom();
        String dominio   = extrairDominio(remetente);

        // 0. Whitelist — domínios confiáveis nunca são bloqueados pelo banco
        if (TRUSTED_SENDERS_WHITELIST.contains(dominio)) {
            return phishingService.analisar(email);
        }

        // 1. Consulta banco — remetente já conhecido como golpista?
        if (emailRepo.existsByRemetente(remetente)) {
            return new AnalyseDTO("FRAUDE", 100,
                List.of("Remetente já identificado como golpista no histórico"));
        }

        // 2. Consulta banco — domínio já conhecido como phishing?
        if (urlRepo.existsByDominio(dominio)) {
            return new AnalyseDTO("FRAUDE", 95,
                List.of("Domínio do remetente consta na base de URLs de phishing"));
        }

        // 3. Heurística base
        AnalyseDTO base = phishingService.analisar(email);
        int score = base.getScore();
        List<String> motivos = new ArrayList<>(base.getMotivos());

        // 4. SafeBrowsing nos links
        List<String> links = LinkExtractor.extrairLinks(email.getBody());
        if (!links.isEmpty()) {
            for (String link : links) {
                String linkDomain = extrairDominioDeUrl(link);
                if (urlRepo.existsByDominio(linkDomain)) {
                    score += 40;
                    motivos.add("Link no email consta na base de phishing: " + linkDomain);
                    break;
                }
            }
            if (safeBrowsingService.isMalicious(links)) {
                score += 30;
                motivos.add("SafeBrowsing: link malicioso confirmado");
            }
        }

        score = Math.max(0, Math.min(score, 100));

        // 5. IA para casos intermediários
        if (score >= 25 && score <= 80) {
            AnalyseDTO ia = aiAnalyseService.analisarComIA(email, score, motivos);
            score = (int) (score * 0.4 + ia.getScore() * 0.6);
            score = Math.max(0, Math.min(score, 100));
            motivos.addAll(ia.getMotivos());
        }

        String classificacao = score < 25 ? "SEGURO"
                             : score < 55 ? "SUSPEITO"
                             : "FRAUDE";

        // 6. Salva apenas se não for domínio confiável
        if (!classificacao.equals("SEGURO")
                && !TRUSTED_SENDERS_WHITELIST.contains(dominio)) {
            salvarSeNovo(email, dominio, classificacao, score, motivos);
            salvarLinksPhishing(links, classificacao);
        }

        return new AnalyseDTO(classificacao, score, motivos);
    }

    private void salvarSeNovo(GmailDTO email, String dominio, String classificacao,
                               int score, List<String> motivos) {
        try {
            if (!emailRepo.existsByRemetente(email.getFrom())) {
                emailRepo.save(new EmailGolpistas(
                    email.getFrom(), dominio, email.getSubject(),
                    email.getBody().substring(0, Math.min(500, email.getBody().length())),
                    classificacao, score, motivos
                ));
                System.out.println(">>> Salvo no banco: " + email.getFrom());
            }
        } catch (Exception e) {
            System.out.println(">>> Erro ao salvar email: " + e.getMessage());
        }
    }

    private void salvarLinksPhishing(List<String> links, String classificacao) {
        if (!classificacao.equals("FRAUDE")) return;
        for (String link : links) {
            try {
                String dominio = extrairDominioDeUrl(link);
                if (!dominio.isBlank() && !urlRepo.existsByUrl(link)) {
                    urlRepo.save(new UrlPhishing(link, dominio, "Detectado pelo sistema"));
                }
            } catch (Exception e) {
                System.out.println(">>> Erro ao salvar URL: " + e.getMessage());
            }
        }
    }

    private String extrairDominio(String from) {
        try {
            if (from.contains("@")) {
                String d = from.substring(from.indexOf("@") + 1)
                               .replaceAll("[>\"'\\s]", "").trim();
                String[] parts = d.split("\\.");
                if (parts.length >= 2) {
                    String last2 = parts[parts.length - 2] + "." + parts[parts.length - 1];
                    // Trata .com.br, .org.br, .gov.br
                    if ((last2.equals("com.br") || last2.equals("org.br")
                            || last2.equals("gov.br")) && parts.length >= 3) {
                        return parts[parts.length - 3] + "." + last2;
                    }
                    return last2;
                }
                return d;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extrairDominioDeUrl(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return "";
            host = host.replace("www.", "");
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return host;
        } catch (Exception e) { return ""; }
    }

    @Deprecated
    public AnalyseDTO analisar(GmailDTO email) {
        return analisarFluxoCompleto(email);
    }
}
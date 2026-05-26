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

    private static final Set<String> TRUSTED_SENDERS_WHITELIST = Set.of(
        "linkedin.com", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "youtube.com",
        "google.com", "gmail.com", "microsoft.com", "github.com",
        "apple.com", "discord.com", "slack.com", "zoom.us",
        "amazon.com", "amazon.com.br", "mercadolivre.com.br",
        "mercadopago.com.br", "shopee.com.br", "americanas.com.br",
        "shein.com", "aliexpress.com", "magalu.com.br",
        "nubank.com.br", "itau.com.br", "bradesco.com.br",
        "santander.com.br", "bb.com.br", "caixa.gov.br",
        "inter.co", "c6bank.com.br", "paypal.com",
        "netflix.com", "spotify.com", "disneyplus.com",
        "hbomax.com", "primevideo.com",
        "ifood.com.br", "uber.com", "99app.com",
        "correios.com.br", "gov.br", "receita.fazenda.gov.br",
        "animaeducacao.com.br", "kroton.com.br", "cogna.com.br",
        "anhanguera.com", "unopar.br",
        "licdn.com", "sendgrid.net", "amazonses.com",
        "mailchimp.com", "klaviyo.com", "mailgun.org",
        "substack.com", "beehiiv.com"
    );

    // Links desses domínios NUNCA são salvos como phishing
    private static final Set<String> LINK_WHITELIST = Set.of(
        "linkedin.com", "licdn.com", "google.com", "googleapis.com",
        "microsoft.com", "apple.com", "amazon.com", "amazon.com.br",
        "facebook.com", "instagram.com", "twitter.com", "x.com",
        "youtube.com", "github.com", "spotify.com", "netflix.com",
        "nubank.com.br", "itau.com.br", "bradesco.com.br",
        "mercadolivre.com.br", "shopee.com.br", "shein.com",
        "ifood.com.br", "uber.com", "gov.br", "correios.com.br",
        "w3.org", "schema.org", "gstatic.com", "googleusercontent.com",
        "sendgrid.net", "amazonses.com", "mailchimp.com",
        "unsubscribe", "optout", "manage-preferences"
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
                // Só verifica no banco se não for domínio da whitelist
                if (!isLinkConfiavel(linkDomain) && urlRepo.existsByDominio(linkDomain)) {
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

        // 6. Salva apenas se não for domínio confiável e score muito alto
        if (classificacao.equals("FRAUDE")
                && !TRUSTED_SENDERS_WHITELIST.contains(dominio)
                && score >= 80) { // ← só salva com alta certeza
            salvarSeNovo(email, dominio, classificacao, score, motivos);
            salvarLinksPhishing(links);
        }

        return new AnalyseDTO(classificacao, score, motivos);
    }

    private boolean isLinkConfiavel(String dominio) {
        return LINK_WHITELIST.stream().anyMatch(d ->
            dominio.equals(d) || dominio.endsWith("." + d));
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

    private void salvarLinksPhishing(List<String> links) {
        for (String link : links) {
            try {
                String dominio = extrairDominioDeUrl(link);
                // Nunca salva domínios da whitelist
                if (dominio.isBlank() || isLinkConfiavel(dominio)) continue;
                if (!urlRepo.existsByUrl(link)) {
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
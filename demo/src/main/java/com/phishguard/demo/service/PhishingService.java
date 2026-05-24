package com.phishguard.demo.service;

import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.util.LinkExtractor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
public class PhishingService {

    private static final Set<String> TRUSTED_DOMAINS = Set.of(
        // Tech
        "google.com", "microsoft.com", "github.com", "apple.com",
        "discord.com", "notion.so", "slack.com", "zoom.us",
        "epicgames.com", "steampowered.com", "twitch.tv", "ea.com",
        "perplexity.ai", "openai.com", "anthropic.com",
        // E-commerce / Finanças BR
        "mercadolivre.com.br", "mercadopago.com.br", "nubank.com.br",
        "itau.com.br", "bradesco.com.br", "santander.com.br",
        "bb.com.br", "caixa.gov.br", "inter.co", "c6bank.com.br",
        "americanas.com.br", "magazineluiza.com.br", "shopee.com.br",
        "amazon.com", "amazon.com.br", "paypal.com",
        // Streaming / Entretenimento
        "netflix.com", "spotify.com", "disneyplus.com",
        "primevideo.com", "hbomax.com",
        // Social / Comunicação
        "linkedin.com", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "youtube.com",
        // Notícias BR
        "globo.com", "uol.com.br", "infomoney.com.br",
        "folha.uol.com.br", "estadao.com.br",
        // Serviços
        "uber.com", "ifood.com.br", "99app.com",
        "adidas.com", "nike.com"
    );

    private static final Set<String> TRUSTED_SENDING_DOMAINS = Set.of(
        "mail.linkedin.com", "e.linkedin.com", "emlnk.com",
        "mail.google.com", "accounts.google.com",
        "mail.microsoft.com", "mail.office365.com",
        "mail.spotify.com", "email.spotify.com",
        "acct.epicgames.com", "email.epicgames.com",
        "mail.perplexity.ai", "em.perplexity.ai",
        "br-news.adidas.com", "news.adidas.com",
        "info.infomoney.com.br", "email.infomoney.com.br",
        "amazonses.com", "sendgrid.net", "mailchimp.com",
        "mandrillapp.com", "sparkpostmail.com", "mailgun.org",
        "exacttarget.com", "salesforce.com", "klaviyo.com",
        "kmail-lists.com", "mcsv.net", "list-manage.com",
        "sounds.cymatics.fm", "substack.com", "beehiiv.com"
    );

    private static final Set<String> HIGH_RISK_SENDERS = Set.of(
        "gmail.com", "hotmail.com", "outlook.com",
        "yahoo.com", "ymail.com", "protonmail.com"
    );

    public AnalyseDTO analisar(GmailDTO email) {
        String from     = safe(email.getFrom()).toLowerCase();
        String body     = safe(email.getBody()).toLowerCase();
        String subject  = safe(email.getSubject()).toLowerCase();

        String domain     = extrairDominioEmail(from);
        String senderRoot = getRootDomain(domain);

        boolean remetenteConfiavel = TRUSTED_DOMAINS.contains(senderRoot)
                || TRUSTED_SENDING_DOMAINS.contains(domain);

        int score = 0;
        Set<String> motivos = new LinkedHashSet<>();

        score += reputationScore(domain, senderRoot, motivos);
        score += contentRisk(body, subject, senderRoot, remetenteConfiavel, motivos);

        List<String> links = LinkExtractor.extrairLinks(email.getBody());
        if (!links.isEmpty()) {
            score += linkAnalysis(links, senderRoot, remetenteConfiavel, motivos);
        }

        score = Math.max(0, Math.min(score, 100));
        return new AnalyseDTO(classificar(score), score, new ArrayList<>(motivos));
    }
    private int reputationScore(String fullDomain, String senderRoot, Set<String> motivos) {
        if (TRUSTED_SENDING_DOMAINS.contains(fullDomain)) {
            motivos.add("Serviço de envio confiável: " + fullDomain);
            return -20;
        }
        if (TRUSTED_DOMAINS.contains(senderRoot)) {
            motivos.add("Domínio confiável: " + senderRoot);
            return -15;
        }
        if (HIGH_RISK_SENDERS.contains(senderRoot)) {
            motivos.add("Remetente em provedor público: " + senderRoot);
            return 15;
        }
        if (senderRoot.isBlank()) {
            motivos.add("Remetente sem domínio identificável");
            return 25;
        }
        motivos.add("Domínio não reconhecido: " + senderRoot);
        return 8; 
    }

    private int contentRisk(String body, String subject, String senderRoot,
                             boolean remetenteConfiavel, Set<String> motivos) {
        int score = 0;
        String text = body + " " + subject;

        if (containsAny(text, "urgente", "imediato", "bloqueado",
                "suspenso", "encerrado", "expira hoje", "última chance",
                "sua conta será", "acesso será bloqueado")) {
            int peso = remetenteConfiavel ? 10 : 25;
            score += peso;
            motivos.add("Linguagem de urgência detectada");
        }
        if (containsAny(text, "confirme sua senha", "atualize sua senha",
                "informe seu cpf", "dados bancários", "cartão de crédito",
                "verifique sua conta agora", "token de acesso")) {
            int peso = remetenteConfiavel ? 5 : 25;
            score += peso;
            motivos.add("Possível solicitação de dados sensíveis");
        }
        if (containsAny(text, "você ganhou", "parabéns você foi selecionado",
                "resgate seu prêmio", "sorteio exclusivo", "clique para resgatar")) {
            score += 25;
            motivos.add("Isca de prêmio ou promoção falsa");
        }
        if (containsAny(subject, "sua conta foi suspensa", "acesso bloqueado",
                "verificação obrigatória", "ação necessária urgente",
                "seu acesso expira")) {
            score += 20;
            motivos.add("Assunto com padrão típico de phishing");
        }

        return score;
    }
    private int linkAnalysis(List<String> links, String senderRoot,
                              boolean remetenteConfiavel, Set<String> motivos) {
        int score = 0;
        boolean adicionouExterno    = false;
        boolean adicionouEncurtador = false;
        boolean adicionouPadrao     = false;
        boolean adicionouIP         = false;

        for (String link : links) {
            String domain = extrairDominio(link);
            String root   = getRootDomain(domain);

            if (!adicionouExterno && !remetenteConfiavel
                    && !root.isBlank() && !root.equals(senderRoot)
                    && !TRUSTED_DOMAINS.contains(root)
                    && !TRUSTED_SENDING_DOMAINS.contains(domain)) {
                score += 12;
                motivos.add("Contém link para domínio externo: " + root);
                adicionouExterno = true;
            }
            if (!adicionouEncurtador && containsAny(link,
                    "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "rb.gy", "cutt.ly")) {
                score += 20;
                motivos.add("Encurtador de URL detectado");
                adicionouEncurtador = true;
            }
            if (!adicionouPadrao && !remetenteConfiavel
                    && link.matches("(?i).*(login|verify|secure|password|confirm|validate|update-account).*")) {
                score += 10;
                motivos.add("URL com padrão suspeito");
                adicionouPadrao = true;
            }
            if (!adicionouIP
                    && link.matches("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) {
                score += 35;
                motivos.add("Link aponta diretamente para endereço IP");
                adicionouIP = true;
            }
        }
        return Math.min(score, 40);
    }
    private boolean containsAny(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
    private String safe(String s) { return s == null ? "" : s; }

    private String classificar(int score) {
        if (score < 25) return "SEGURO";
        if (score < 55) return "SUSPEITO";
        return "FRAUDE";
    }
    private String extrairDominioEmail(String from) {
        try {
            if (from.contains("@")) {
                return from.substring(from.indexOf("@") + 1)
                           .replaceAll("[>\"'\\s]", "").trim();
            }
        } catch (Exception ignored) {}
        return "";
    }
    private String extrairDominio(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.replace("www.", "");
        } catch (Exception e) { return ""; }
    }
    private String getRootDomain(String domain) {
        try {
            if (domain == null || domain.isBlank()) return "";
            String[] parts = domain.split("\\.");
            if (parts.length < 2) return domain;
            String lastTwo = parts[parts.length - 2] + "." + parts[parts.length - 1];
            if (lastTwo.equals("com.br") || lastTwo.equals("org.br") || lastTwo.equals("gov.br")) {
                if (parts.length >= 3) return parts[parts.length - 3] + "." + lastTwo;
            }
            return lastTwo;
        } catch (Exception e) { return domain; }
    }
}
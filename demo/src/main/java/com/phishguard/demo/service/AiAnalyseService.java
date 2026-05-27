package com.phishguard.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiAnalyseService {

    @Value("${groq.api.key}")
    private String apiKey;

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    private final ObjectMapper mapper = new ObjectMapper();

    public AnalyseDTO analisarComIA(GmailDTO email,
                                    int scoreHeuristico,
                                    List<String> motivos) {
        try {
            RestTemplate rest = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                "model", "llama-3.3-70b-versatile",
                "temperature", 0.1,
                "max_tokens", 400,
                "messages", List.of(
                    Map.of("role", "system", "content", buildSystemPrompt()),
                    Map.of("role", "user", "content", buildPrompt(email, scoreHeuristico, motivos))
                )
            );

            System.out.println(">>> Chamando Groq para: " + email.getFrom());

            ResponseEntity<String> res = rest.postForEntity(
                URL, new HttpEntity<>(body, headers), String.class
            );

            if (res.getBody() != null) {
                return parse(res.getBody(), scoreHeuristico, motivos);
            }

        } catch (Exception e) {
            System.out.println(">>> Groq erro: " + e.getMessage());
        }

        return new AnalyseDTO("SUSPEITO", scoreHeuristico, motivos);
    }

    private AnalyseDTO parse(String json, int scoreFallback, List<String> motivosFallback) {
        try {
            JsonNode root = mapper.readTree(json);

            if (root.has("error")) {
                String msg = root.path("error").path("message").asText();
                System.out.println(">>> Groq erro API: " + msg);
                return new AnalyseDTO("SUSPEITO", scoreFallback,
                        List.of("IA indisponível: " + msg));
            }

            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            System.out.println(">>> Resposta IA: " + content);

            String clean = content.replace("```json", "").replace("```", "").trim();
            JsonNode r = mapper.readTree(clean);

            String classificacao = r.path("classificacao").asText("SUSPEITO");
            int score = r.path("score").asInt(scoreFallback);
            String explicacao = r.path("explicacao").asText("");
            String contexto = r.path("contexto").asText("");

            List<String> motivosIA = new ArrayList<>();
            if (!explicacao.isBlank()) motivosIA.add("Análise: " + explicacao);
            if (!contexto.isBlank()) motivosIA.add("Contexto: " + contexto);

            // Valida classificação
            if (!List.of("SEGURO", "SUSPEITO", "FRAUDE").contains(classificacao)) {
                classificacao = "SUSPEITO";
            }

            return new AnalyseDTO(classificacao, score, motivosIA);

        } catch (Exception e) {
            System.out.println(">>> Parse erro: " + e.getMessage());
            return new AnalyseDTO("SUSPEITO", scoreFallback, motivosFallback);
        }
    }

    private String buildSystemPrompt() {
        return """
        Você é um especialista sênior em segurança cibernética especializado em detecção de phishing e engenharia social.

        Suas responsabilidades:
        - Analisar emails para identificar tentativas de phishing, fraude ou engenharia social
        - Distinguir emails legítimos de marketing/newsletter de ameaças reais
        - Considerar contexto: emails de empresas conhecidas com links de rastreamento são normais
        - Ser preciso: evitar falsos positivos em emails legítimos de empresas reais

        Regras importantes:
        - Emails de newsletters e marketing de empresas conhecidas NÃO são phishing
        - Links de rastreamento em emails de marketing são normais (ex: links longos com parâmetros UTM)
        - Gmail como remetente é neutro por si só, analise o conteúdo
        - Phishing real geralmente pede dados sensíveis, cria urgência falsa ou usa domínios imitadores

        Responda APENAS com JSON puro, sem markdown, sem texto fora do JSON.
        """;
    }

    private String buildPrompt(GmailDTO email, int score, List<String> motivos) {
        return """
        Analise este email quanto ao risco de phishing:

        De: %s
        Assunto: %s
        Corpo (primeiros 1000 chars): %s

        Score heurístico atual: %d/100
        Indicadores detectados: %s

        Responda com este JSON:
        {
          "classificacao": "SEGURO|SUSPEITO|FRAUDE",
          "score": <número 0-100>,
          "explicacao": "<o que torna este email seguro ou perigoso, em 1 frase objetiva>",
          "contexto": "<contexto adicional relevante: tipo de email, empresa remetente, padrão observado>"
        }
        """.formatted(
                email.getFrom(),
                email.getSubject(),
                email.getBody().substring(0, Math.min(1000, email.getBody().length())),
                score,
                String.join(", ", motivos)
        );
    }
}
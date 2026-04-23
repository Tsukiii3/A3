package com.phishguard.demo.service;

import com.phishguard.demo.dto.AnalyseDTO;
import com.phishguard.demo.dto.GmailDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class AiAnalyseService {

    private final String apiKey = "AIzaSyBy07ZG6GTggHje5NqK8kwfUyTRpWaaQ8A"; 
    // URL limpa usando o modelo flash-latest que você encontrou
    private final String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + apiKey;

    public AnalyseDTO analisarComIA(GmailDTO email, List<String> motivosHeuristica) {
        
        // PROMPT DE ENGENHARIA SOCIAL: Ensinando a IA o que procurar
        String prompt = "Analise este email para phishing e engenharia social.\n" +
                "Remetente: " + email.getFrom() + "\n" +
                "Corpo: " + email.getBody() + "\n" +
                "Responda apenas JSON: {\"classificacao\": \"FRAUDE\", \"score\": 100}";

        try {
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Header específico exigido pelo Google em algumas regiões
            headers.set("X-goog-api-key", apiKey);

            // Estrutura de Body idêntica ao seu CURL
            Map<String, Object> textPart = Map.of("text", prompt);
            Map<String, Object> parts = Map.of("parts", List.of(textPart));
            Map<String, Object> contents = Map.of("contents", List.of(parts));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(contents, headers);
            
            ResponseEntity<Map> response = rest.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return extrairVeredito(response.getBody());
            }
            
            return null; // Deixa o Orchestrator usar a heurística

        } catch (Exception e) {
            System.err.println("Falha na comunicação com Gemini: " + e.getMessage());
            return null; // Silencioso para não travar o fluxo
        }
    }

    private AnalyseDTO extrairVeredito(Map responseBody) {
        try {
            // Navegação no JSON de resposta do Google
            List candidates = (List) responseBody.get("candidates");
            Map firstCandidate = (Map) candidates.get(0);
            Map content = (Map) firstCandidate.get("content");
            List parts = (List) content.get("parts");
            String rawText = (String) ((Map) parts.get(0)).get("text");

            // Limpeza de possíveis markdown (```json ... ```)
            String cleanJson = rawText.replaceAll("```json", "").replaceAll("```", "").trim();
            
            // Lógica de Veredito
            boolean ehFraude = cleanJson.toUpperCase().contains("FRAUDE");
            
            List<String> motivos = new ArrayList<>();
            motivos.add("IA: Análise de Engenharia Social Concluída");
            
            // Tenta extrair a justificativa da IA se o JSON estiver bem formatado
            if (cleanJson.contains("analise")) {
                 motivos.add("Detalhe: " + cleanJson);
            }

            return new AnalyseDTO(
                ehFraude ? "FRAUDE" : "SEGURO", 
                ehFraude ? 100 : 0, 
                motivos
            );
            
        } catch (Exception e) {
            System.err.println("Erro ao processar JSON da IA: " + e.getMessage());
            return null;
        }
    }
}
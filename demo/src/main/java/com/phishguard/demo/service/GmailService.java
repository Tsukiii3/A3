package com.phishguard.demo.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import com.phishguard.demo.dto.GmailDTO;
import com.phishguard.demo.model.Usuario;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GmailService {

    private static final String APP_NAME   = "PhishGuard";
    private static final GsonFactory JSON  = GsonFactory.getDefaultInstance();
    private static final String CREDS_PATH = "/credentials/credentials.json";
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND);

    public TokenInfo trocarCodigoPorToken(String code) throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets secrets = carregarSecrets();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            transport, JSON, secrets, SCOPES)
            .setAccessType("offline")
            .build();

        GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
            .setRedirectUri(System.getenv("GOOGLE_REDIRECT_URI")) // ← corrigido
            .execute();

        Credential credential = flow.createAndStoreCredential(tokenResponse, "temp");
        Gmail gmail = new Gmail.Builder(transport, JSON, credential)
            .setApplicationName(APP_NAME).build();

        com.google.api.services.gmail.model.Profile profile =
            gmail.users().getProfile("me").execute();

        String email        = profile.getEmailAddress();
        String accessToken  = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken() != null
            ? tokenResponse.getRefreshToken() : "";

        LocalDateTime expiracao = LocalDateTime.now()
            .plusSeconds(tokenResponse.getExpiresInSeconds() != null
                ? tokenResponse.getExpiresInSeconds() : 3600);

        return new TokenInfo(email, email.split("@")[0],
                             accessToken, refreshToken, expiracao);
    }
    public void enviarEmail(Usuario usuario, String para, String assunto, String corpo) throws Exception {
    Gmail service = getServiceParaUsuario(usuario);

    // Monta o email em formato MIME
    String emailRaw = "To: " + para + "\r\n"
                    + "Subject: " + assunto + "\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "\r\n"
                    + corpo;

    byte[] emailBytes = emailRaw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String encoded = Base64.getUrlEncoder().encodeToString(emailBytes);

    Message message = new Message();
    message.setRaw(encoded);

    service.users().messages().send("me", message).execute();
}

    public List<GmailDTO> buscarEmails(Usuario usuario) throws Exception {
        Gmail service = getServiceParaUsuario(usuario);

        ListMessagesResponse response = service.users().messages()
            .list("me").setMaxResults(10L).execute();

        List<Message> messages = response.getMessages();
        List<GmailDTO> emails  = new ArrayList<>();
        if (messages == null) return emails;

        for (Message msg : messages) {
            Message full = service.users().messages()
                .get("me", msg.getId()).execute();

            String subject = "", from = "";
            for (MessagePartHeader h : full.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(h.getName())) subject = h.getValue();
                if ("From".equalsIgnoreCase(h.getName()))    from    = h.getValue();
            }

            emails.add(new GmailDTO(from, subject, extractBody(full.getPayload())));
        }

        return emails;
    }

    public List<GmailDTO> buscarEmails() throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets secrets = carregarSecrets();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            transport, JSON, secrets, SCOPES)
            .setDataStoreFactory(
                new com.google.api.client.util.store.FileDataStoreFactory(
                    new java.io.File("tokens")))
            .setAccessType("offline")
            .build();

        com.google.api.client.extensions.java6.auth.oauth2
            .AuthorizationCodeInstalledApp app =
            new com.google.api.client.extensions.java6.auth.oauth2
                .AuthorizationCodeInstalledApp(
                flow,
                new com.google.api.client.extensions.jetty.auth.oauth2
                    .LocalServerReceiver());

        Credential credential = app.authorize("user");

        Gmail gmail = new Gmail.Builder(transport, JSON, credential)
            .setApplicationName(APP_NAME).build();

        ListMessagesResponse response = gmail.users().messages()
            .list("me").setMaxResults(50L).execute();

        List<Message> messages = response.getMessages();
        List<GmailDTO> emails  = new ArrayList<>();
        if (messages == null) return emails;

        for (Message msg : messages) {
            Message full = gmail.users().messages()
                .get("me", msg.getId()).execute();

            String subject = "", from = "";
            for (MessagePartHeader h : full.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(h.getName())) subject = h.getValue();
                if ("From".equalsIgnoreCase(h.getName()))    from    = h.getValue();
            }

            emails.add(new GmailDTO(from, subject, extractBody(full.getPayload())));
        }

        return emails;
    }

   private Gmail getServiceParaUsuario(Usuario usuario) throws Exception {
    NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
    GoogleClientSecrets secrets = carregarSecrets();

    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        transport, JSON, secrets, SCOPES)
        .setAccessType("offline")
        .build();

    GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
    tokenResponse.setAccessToken(usuario.getGmailAccessToken());
    tokenResponse.setRefreshToken(usuario.getGmailRefreshToken());
    tokenResponse.setTokenType("Bearer");
    tokenResponse.setExpiresInSeconds(3600L);

    Credential credential = flow.createAndStoreCredential(
        tokenResponse, usuario.getEmail());

    // ← adiciona refresh automático
    if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
        credential.refreshToken();
    }

    return new Gmail.Builder(transport, JSON, credential)
        .setApplicationName(APP_NAME).build();
}

    private GoogleClientSecrets carregarSecrets() throws Exception {
        String credsJson = System.getenv("GOOGLE_CREDENTIALS");
        if (credsJson != null && !credsJson.isBlank()) {
            return GoogleClientSecrets.load(JSON,
                new InputStreamReader(
                    new ByteArrayInputStream(credsJson.getBytes())));
        }
        InputStream in = GmailService.class.getResourceAsStream(CREDS_PATH);
        if (in == null) throw new RuntimeException("credentials.json não encontrado!");
        return GoogleClientSecrets.load(JSON, new InputStreamReader(in));
    }

    private String extractBody(MessagePart payload) {
    try {
        // Tenta pegar text/html primeiro, depois text/plain
        String html  = extractPart(payload, "text/html");
        if (html != null && !html.isBlank()) return html;

        String plain = extractPart(payload, "text/plain");
        if (plain != null && !plain.isBlank()) return plain;

        // Fallback direto no body
        if (payload.getBody() != null && payload.getBody().getData() != null) {
            return new String(
                Base64.getUrlDecoder().decode(payload.getBody().getData()),
                java.nio.charset.StandardCharsets.UTF_8);
        }
    } catch (Exception e) {
        System.out.println("Erro ao extrair body: " + e.getMessage());
    }
    return "";
}

private String extractPart(MessagePart payload, String mimeType) {
    if (mimeType.equals(payload.getMimeType())
            && payload.getBody() != null
            && payload.getBody().getData() != null) {
        return new String(
            Base64.getUrlDecoder().decode(payload.getBody().getData()),
            java.nio.charset.StandardCharsets.UTF_8);
    }
    if (payload.getParts() != null) {
        for (MessagePart part : payload.getParts()) {
            String result = extractPart(part, mimeType);
            if (result != null) return result;
        }
    }
    return null;
}

    public record TokenInfo(
        String email,
        String nome,
        String accessToken,
        String refreshToken,
        LocalDateTime expiracao
    ) {}
}
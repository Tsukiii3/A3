package com.phishguard.demo.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.gmail.*;
import com.google.api.services.gmail.model.*;

import com.phishguard.demo.dto.GmailDTO;

@Service
public class GmailService {

    private static final String APPLICATION_NAME = "PhishGuard";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final List<String> SCOPES =
            Collections.singletonList(GmailScopes.GMAIL_READONLY);

    private static final String CREDENTIALS_FILE_PATH =
            "/credentials/credentials.json";

    private Credential getCredentials(NetHttpTransport HTTP_TRANSPORT) throws Exception {

        InputStream in = GmailService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);

        if (in == null) {
            throw new RuntimeException("credentials.json não encontrado!");
        }

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT,
                        JSON_FACTORY,
                        clientSecrets,
                        SCOPES
                )
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline")
                        .build();

        LocalServerReceiver receiver =
                new LocalServerReceiver.Builder().setPort(8888).build();

        return new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");
    }

    public Gmail getService() throws Exception {

        NetHttpTransport HTTP_TRANSPORT =
                GoogleNetHttpTransport.newTrustedTransport();

        return new Gmail.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                getCredentials(HTTP_TRANSPORT)
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public List<GmailDTO> buscarEmails() throws Exception {

        Gmail service = getService();
        String user = "me";

        ListMessagesResponse response =
                service.users().messages().list(user).setMaxResults(5L).execute();

        List<Message> messages = response.getMessages();
        List<GmailDTO> emails = new ArrayList<>();

        if (messages == null) return emails;

        for (Message msg : messages) {

            Message fullMessage =
                    service.users().messages().get(user, msg.getId()).execute();

            String subject = "";
            String from = "";

            List<MessagePartHeader> headers = fullMessage.getPayload().getHeaders();

            for (MessagePartHeader header : headers) {
                if ("Subject".equalsIgnoreCase(header.getName())) {
                    subject = header.getValue();
                }
                if ("From".equalsIgnoreCase(header.getName())) {
                    from = header.getValue();
                }
            }

            String body = extractBody(fullMessage.getPayload());

            emails.add(new GmailDTO(from, subject, body));
        }

        return emails;
    }

    private String extractBody(MessagePart payload) {

        try {
            if (payload.getBody() != null && payload.getBody().getData() != null) {
                return new String(
                        java.util.Base64.getUrlDecoder().decode(payload.getBody().getData())
                );
            }

            if (payload.getParts() != null) {
                for (MessagePart part : payload.getParts()) {

                    if ("text/plain".equals(part.getMimeType()) &&
                            part.getBody() != null &&
                            part.getBody().getData() != null) {

                        return new String(
                                java.util.Base64.getUrlDecoder().decode(part.getBody().getData())
                        );
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Erro ao extrair body: " + e.getMessage());
        }

        return "";
    }
}
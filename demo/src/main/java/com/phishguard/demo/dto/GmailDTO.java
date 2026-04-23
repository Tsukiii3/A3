package com.phishguard.demo.dto;


public class GmailDTO {

   private String from;
    private String subject;
    private String body;

    public GmailDTO(String from, String subject, String body) {
        this.from = from;
        this.subject = subject;
        this.body = body;
    }

    public String getFrom() { return from; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
}
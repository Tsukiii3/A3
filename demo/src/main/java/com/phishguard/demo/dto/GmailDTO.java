package com.phishguard.demo.dto;

public class GmailDTO {
    private String from;
    private String subject;
    private String body;
    private String gmailId; 

    public GmailDTO(String from, String subject, String body) {
        this.from    = from;
        this.subject = subject;
        this.body    = body;
    }

    public GmailDTO(String from, String subject, String body, String gmailId) {
        this.from    = from;
        this.subject = subject;
        this.body    = body;
        this.gmailId = gmailId;
    }

    public String getFrom()    { return from; }
    public String getSubject() { return subject; }
    public String getBody()    { return body; }
    public String getGmailId() { return gmailId; }

    public String getEmailId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEmailId'");
    }
}
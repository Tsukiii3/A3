package com.phishguard.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "urls_phishing",
       indexes = @Index(name = "idx_dominio", columnList = "dominio"))
public class UrlPhishing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(nullable = false)
    private String dominio;

    private String fonte;

    @Column(nullable = false)
    private LocalDateTime adicionadoEm = LocalDateTime.now();

    public UrlPhishing() {}

    public UrlPhishing(String url, String dominio, String fonte) {
        this.url = url;
        this.dominio = dominio;
        this.fonte = fonte;
        this.adicionadoEm = LocalDateTime.now();
    }
    public Long getId()                   { return id; }
    public String getUrl()                { return url; }
    public String getDominio()            { return dominio; }
    public String getFonte()              { return fonte; }
    public LocalDateTime getAdicionadoEm(){ return adicionadoEm; }
}

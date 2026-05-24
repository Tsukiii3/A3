package com.phishguard.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "emails_golpistas")
public class EmailGolpistas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String remetente;

    @Column(nullable = false)
    private String dominio;

    @Column(nullable = false)
    private String assunto;

    @Column(columnDefinition = "TEXT")
    private String corpo;

    @Column(nullable = false)
    private String classificacao;

    @Column(nullable = false)
    private int score;

    @ElementCollection
    @CollectionTable(name = "email_motivos",
                     joinColumns = @JoinColumn(name = "email_id"))
    @Column(name = "motivo")
    private List<String> motivos;

    @Column(nullable = false)
    private LocalDateTime detectadoEm = LocalDateTime.now();

    public EmailGolpistas() {}

    public EmailGolpistas(String remetente, String dominio, String assunto,
                         String corpo, String classificacao,
                         int score, List<String> motivos) {
        this.remetente = remetente;
        this.dominio = dominio;
        this.assunto = assunto;
        this.corpo = corpo;
        this.classificacao = classificacao;
        this.score = score;
        this.motivos = motivos;
        this.detectadoEm = LocalDateTime.now();
    }
    public Long getId()                  { return id; }
    public String getRemetente()         { return remetente; }
    public String getDominio()           { return dominio; }
    public String getAssunto()           { return assunto; }
    public String getCorpo()             { return corpo; }
    public String getClassificacao()     { return classificacao; }
    public int getScore()                { return score; }
    public List<String> getMotivos()     { return motivos; }
    public LocalDateTime getDetectadoEm(){ return detectadoEm; }
}
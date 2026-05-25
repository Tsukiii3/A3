package com.phishguard.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emails_salvos",
       indexes = {
           @Index(name = "idx_usuario_id", columnList = "usuario_id"),
           @Index(name = "idx_pasta", columnList = "pasta"),
           @Index(name = "idx_gmail_id", columnList = "gmailId")
       })
public class EmailSalvo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String gmailId; // ID original do Gmail para evitar duplicatas

    @Column(nullable = false)
    private String remetente;

    @Column(nullable = false)
    private String assunto;

    @Column(columnDefinition = "TEXT")
    private String corpo;

    @Column(nullable = false)
    private String pasta; // inbox, sent, archive, trash

    private boolean lido = false;
    private boolean favorito = false;

    private String classificacao;
    private Integer score;

    @ElementCollection
    @CollectionTable(name = "email_salvo_motivos",
                     joinColumns = @JoinColumn(name = "email_id"))
    @Column(name = "motivo")
    private java.util.List<String> motivos = new java.util.ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime recebidoEm = LocalDateTime.now();

    public EmailSalvo() {}

    public EmailSalvo(Usuario usuario, String gmailId, String remetente,
                      String assunto, String corpo, String pasta) {
        this.usuario   = usuario;
        this.gmailId   = gmailId;
        this.remetente = remetente;
        this.assunto   = assunto;
        this.corpo     = corpo;
        this.pasta     = pasta;
        this.recebidoEm = LocalDateTime.now();
    }

    public Long getId()                    { return id; }
    public Usuario getUsuario()            { return usuario; }
    public String getGmailId()             { return gmailId; }
    public String getRemetente()           { return remetente; }
    public String getAssunto()             { return assunto; }
    public String getCorpo()               { return corpo; }
    public String getPasta()               { return pasta; }
    public boolean isLido()                { return lido; }
    public boolean isFavorito()            { return favorito; }
    public String getClassificacao()       { return classificacao; }
    public Integer getScore()              { return score; }
    public java.util.List<String> getMotivos() { return motivos; }
    public LocalDateTime getRecebidoEm()   { return recebidoEm; }

    public void setLido(boolean lido)              { this.lido = lido; }
    public void setFavorito(boolean favorito)      { this.favorito = favorito; }
    public void setPasta(String pasta)             { this.pasta = pasta; }
    public void setClassificacao(String c)         { this.classificacao = c; }
    public void setScore(Integer score)            { this.score = score; }
    public void setMotivos(java.util.List<String> m) { this.motivos = m; }
}

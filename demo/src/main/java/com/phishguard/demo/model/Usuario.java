package com.phishguard.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nome;

    // Token de acesso do Gmail salvo no banco
    @Column(columnDefinition = "TEXT")
    private String gmailAccessToken;

    @Column(columnDefinition = "TEXT")
    private String gmailRefreshToken;

    @Column
    private LocalDateTime tokenExpiracao;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Usuario() {}

    public Usuario(String email, String nome) {
        this.email    = email;
        this.nome     = nome;
        this.criadoEm = LocalDateTime.now();
    }

    // Getters e Setters
    public Long getId()                      { return id; }
    public String getEmail()                 { return email; }
    public String getNome()                  { return nome; }
    public String getGmailAccessToken()      { return gmailAccessToken; }
    public String getGmailRefreshToken()     { return gmailRefreshToken; }
    public LocalDateTime getTokenExpiracao() { return tokenExpiracao; }
    public LocalDateTime getCriadoEm()       { return criadoEm; }

    public void setGmailAccessToken(String t)      { this.gmailAccessToken = t; }
    public void setGmailRefreshToken(String t)     { this.gmailRefreshToken = t; }
    public void setTokenExpiracao(LocalDateTime t) { this.tokenExpiracao = t; }
    public void setNome(String nome)               { this.nome = nome; }
}
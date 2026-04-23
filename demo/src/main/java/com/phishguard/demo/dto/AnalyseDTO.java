package com.phishguard.demo.dto;

import java.util.ArrayList;
import java.util.List;

public class AnalyseDTO {
    private String classificacao;
    private int score;
    private List<String> motivos = new ArrayList<>(); // Inicialização evita o erro ' '

    // CONSTRUTOR PADRÃO (Obrigatório para o Jackson converter para JSON)
    public AnalyseDTO() {
    }

    public AnalyseDTO(String classificacao, int score, List<String> motivos) {
        this.classificacao = classificacao;
        this.score = score;
        this.motivos = (motivos != null) ? motivos : new ArrayList<>();
    }

    // Getters e Setters
    public String getClassificacao() { return classificacao; }
    public void setClassificacao(String classificacao) { this.classificacao = classificacao; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<String> getMotivos() { return motivos; }
    public void setMotivos(List<String> motivos) { 
        this.motivos = (motivos != null) ? motivos : new ArrayList<>(); 
    }
}
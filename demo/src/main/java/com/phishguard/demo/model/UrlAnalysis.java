package com.phishguard.demo.model;

import jakarta.persistence.*;

@Entity
public class UrlAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;
    private String status;
    private int score;

    public Long getId() {
        return id;
    }
    public int getScore() {
        return score;
    }
    public String getStatus() {
        return status;
    }
    public String getUrl() {
        return url;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public void setScore(int score) {
        this.score = score;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public void setUrl(String url) {
        this.url = url;
    }
}

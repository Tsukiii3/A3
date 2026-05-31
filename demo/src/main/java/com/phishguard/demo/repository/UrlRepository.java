package com.phishguard.demo.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.phishguard.demo.model.UrlAnalysis;

public interface UrlRepository extends JpaRepository<UrlAnalysis, Long> {
    }

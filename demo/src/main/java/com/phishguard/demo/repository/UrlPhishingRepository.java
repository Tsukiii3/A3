package com.phishguard.demo.repository;

import com.phishguard.demo.model.UrlPhishing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrlPhishingRepository extends JpaRepository<UrlPhishing, Long> {
    boolean existsByUrl(String url);
    boolean existsByDominio(String dominio);
    List<UrlPhishing> findByDominio(String dominio);
}

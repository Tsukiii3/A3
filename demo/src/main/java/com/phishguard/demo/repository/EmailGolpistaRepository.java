package com.phishguard.demo.repository;

import com.phishguard.demo.model.EmailGolpistas;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailGolpistaRepository extends JpaRepository<EmailGolpistas, Long> {
    boolean existsByRemetente(String remetente);
    void deleteByRemetente(String remetente);
    List<EmailGolpistas> findByDominio(String dominio);
    List<EmailGolpistas> findByClassificacao(String classificacao);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailGolpistas e WHERE e.dominio = :dominio")
    void deleteByDominio(@org.springframework.data.repository.query.Param("dominio") String dominio);
}
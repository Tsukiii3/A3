package com.phishguard.demo.repository;

import com.phishguard.demo.model.EmailSalvo;
import com.phishguard.demo.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailSalvoRepository extends JpaRepository<EmailSalvo, Long> {
    List<EmailSalvo> findByUsuarioAndPastaOrderByRecebidoEmDesc(Usuario usuario, String pasta);
    List<EmailSalvo> findByUsuarioAndFavoritoTrueOrderByRecebidoEmDesc(Usuario usuario);
    List<EmailSalvo> findByUsuarioOrderByRecebidoEmDesc(Usuario usuario);
    boolean existsByUsuarioAndGmailId(Usuario usuario, String gmailId);
    Optional<EmailSalvo> findByUsuarioAndId(Usuario usuario, Long id);

    @Modifying
    @Transactional
    @Query("UPDATE EmailSalvo e SET e.lido = true WHERE e.usuario = :usuario AND e.id = :id")
    void marcarComoLido(Usuario usuario, Long id);

    @Modifying
    @Transactional
    @Query("UPDATE EmailSalvo e SET e.favorito = :favorito WHERE e.usuario = :usuario AND e.id = :id")
    void atualizarFavorito(Usuario usuario, Long id, boolean favorito);

    @Modifying
    @Transactional
    @Query("UPDATE EmailSalvo e SET e.pasta = :pasta WHERE e.usuario = :usuario AND e.id = :id")
    void moverParaPasta(Usuario usuario, Long id, String pasta);
}
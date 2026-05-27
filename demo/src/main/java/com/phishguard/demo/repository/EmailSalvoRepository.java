package com.phishguard.demo.repository;

import com.phishguard.demo.model.EmailSalvo;
import com.phishguard.demo.model.Usuario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailSalvoRepository extends JpaRepository<EmailSalvo, Long> {

    // Com paginação
    List<EmailSalvo> findByUsuarioAndPastaOrderByRecebidoEmDesc(Usuario usuario, String pasta, Pageable pageable);
    List<EmailSalvo> findByUsuarioAndFavoritoTrueOrderByRecebidoEmDesc(Usuario usuario, Pageable pageable);

    // Sem paginação (mantém para compatibilidade)
    List<EmailSalvo> findByUsuarioAndPastaOrderByRecebidoEmDesc(Usuario usuario, String pasta);
    List<EmailSalvo> findByUsuarioAndFavoritoTrueOrderByRecebidoEmDesc(Usuario usuario);
    List<EmailSalvo> findByUsuarioOrderByRecebidoEmDesc(Usuario usuario);

    boolean existsByUsuarioAndGmailId(Usuario usuario, String gmailId);
    Optional<EmailSalvo> findByUsuarioAndId(Usuario usuario, Long id);

    List<EmailSalvo> findByUsuarioAndClassificacaoIsNull(Usuario usuario);

    @Modifying
    @Transactional
    @Query("UPDATE EmailSalvo e SET e.lido = true WHERE e.usuario = :usuario AND e.id = :id")
    void marcarComoLido(@Param("usuario") Usuario usuario, @Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE EmailSalvo e SET e.favorito = :favorito WHERE e.usuario = :usuario AND e.id = :id")
    void atualizarFavorito(@Param("usuario") Usuario usuario, @Param("id") Long id, @Param("favorito") boolean favorito);

    @Modifying
    @Transactional
    @Query("UPDATE EmailSalvo e SET e.pasta = :pasta WHERE e.usuario = :usuario AND e.id = :id")
    void moverParaPasta(@Param("usuario") Usuario usuario, @Param("id") Long id, @Param("pasta") String pasta);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM emails_salvos
        WHERE id NOT IN (
            SELECT MIN(id) FROM emails_salvos
            GROUP BY usuario_id, gmail_id
        )
        """, nativeQuery = true)
    void removerDuplicatas();
}
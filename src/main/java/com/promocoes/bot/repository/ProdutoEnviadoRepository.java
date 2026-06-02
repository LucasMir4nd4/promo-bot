package com.promocoes.bot.repository;

import com.promocoes.bot.model.ProdutoEnviado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para verificar e persistir produtos já enviados.
 */
@Repository
public interface ProdutoEnviadoRepository extends JpaRepository<ProdutoEnviado, Long> {

    /**
     * Verifica se o produto (por ASIN) já foi enviado anteriormente.
     */
    boolean existsByAsin(String asin);

    /**
     * Busca produto pelo ASIN.
     */
    Optional<ProdutoEnviado> findByAsin(String asin);

    /**
     * Retorna produtos enviados nas últimas X horas.
     */
    List<ProdutoEnviado> findByEnviadoEmAfter(LocalDateTime dataHora);

    /**
     * Conta quantos produtos foram enviados hoje.
     */
    @Query("SELECT COUNT(p) FROM ProdutoEnviado p WHERE p.enviadoEm >= :inicio AND p.enviadoEm < :fim")
    long countEnviadosNoPeriodo(LocalDateTime inicio, LocalDateTime fim);
}

package com.promocoes.bot.repository;

import com.promocoes.bot.model.LinkFixo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkFixoRepository extends JpaRepository<LinkFixo, Long> {
    List<LinkFixo> findByAtivoTrue();

    /** Pendentes de revisão: capturados pelo bot e ainda sem link de afiliado preenchido. */
    List<LinkFixo> findByLinkAfiliadoIsNullOrderByIdDesc();

    Optional<LinkFixo> findByMlbId(String mlbId);

    boolean existsByMlbId(String mlbId);
}

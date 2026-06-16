package com.promocoes.bot.repository;

import com.promocoes.bot.model.LinkFixo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkFixoRepository extends JpaRepository<LinkFixo, Long> {
    List<LinkFixo> findByAtivoTrue();
    boolean existsByMlbId(String mlbId);
}

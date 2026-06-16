package com.promocoes.bot.repository;

import com.promocoes.bot.model.MercadoLivreToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MercadoLivreTokenRepository extends JpaRepository<MercadoLivreToken, Long> {
    // Sempre usamos a linha de id = 1L. findById(1L) já resolve.
}

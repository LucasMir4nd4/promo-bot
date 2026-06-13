package com.promocoes.bot.repository;

import com.promocoes.bot.model.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigRepository extends JpaRepository<ConfigEntry, String> {
}

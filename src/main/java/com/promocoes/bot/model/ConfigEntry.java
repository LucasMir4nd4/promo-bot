package com.promocoes.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfigEntry {

    @Id
    @Column(length = 100)
    private String chave;

    @Column(nullable = false, length = 2000)
    private String valor;
}

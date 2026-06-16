package com.promocoes.bot.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "links_fixos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkFixo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mlb_id", nullable = false, unique = true)
    private String mlbId;

    @Column(name = "link_afiliado", nullable = false, length = 2000)
    private String linkAfiliado;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;
}

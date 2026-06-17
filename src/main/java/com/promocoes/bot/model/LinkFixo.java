package com.promocoes.bot.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    /**
     * Link de afiliado gerado na mão. Fica nulo enquanto o item está pendente
     * (capturado pelo bot mas ainda sem link). Só pode ser ativado depois de preenchido.
     */
    @Column(name = "link_afiliado", length = 2000)
    private String linkAfiliado;

    /**
     * true = pronto para postar (link preenchido e aprovado).
     * Itens recém-capturados pelo bot nascem como false (pendentes).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = false;

    // ── Snapshot do produto capturado pelo bot (usado na revisão e na publicação) ──

    @Column(length = 500)
    private String titulo;

    private BigDecimal precoAtual;

    private BigDecimal precoOriginal;

    private Integer percentualDesconto;

    @Column(length = 1000)
    private String urlImagem;

    @Column(length = 2000)
    private String urlProduto;

    private String categoria;
}

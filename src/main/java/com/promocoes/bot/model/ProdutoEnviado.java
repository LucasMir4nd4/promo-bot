package com.promocoes.bot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade que representa um produto já enviado.
 * Usada para evitar envio duplicado de promoções.
 */
@Entity
@Table(name = "produtos_enviados")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ASIN único do produto na Amazon */
    @Column(nullable = false, unique = true)
    private String asin;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(name = "preco_atual", precision = 10, scale = 2)
    private BigDecimal precoAtual;

    @Column(name = "preco_original", precision = 10, scale = 2)
    private BigDecimal precoOriginal;

    @Column(name = "percentual_desconto")
    private Integer percentualDesconto;

    @Column(name = "url_imagem", length = 2000)
    private String urlImagem;

    @Column(name = "url_afiliado", length = 2000)
    private String urlAfiliado;

    /** Categoria do produto na Amazon */
    @Column(length = 100)
    private String categoria;

    /** Data/hora que a promoção foi enviada */
    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    /** Indica se foi enviado para o Telegram */
    @Column(name = "enviado_telegram")
    @Builder.Default
    private Boolean enviadoTelegram = false;

    /** Indica se foi enviado para o WhatsApp */
    @Column(name = "enviado_whatsapp")
    @Builder.Default
    private Boolean enviadoWhatsapp = false;

    @PrePersist
    protected void onCreate() {
        enviadoEm = LocalDateTime.now();
    }
}

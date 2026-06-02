package com.promocoes.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO com os dados do produto vindos da API da Amazon.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoMLDTO {

    private String asin;
    private String titulo;
    private BigDecimal precoAtual;
    private BigDecimal precoOriginal;
    private Integer percentualDesconto;
    private String urlImagem;
    private String urlProduto;  // URL base do produto
    private String urlAfiliado; // URL com a tag de afiliado
    private String categoria;

    /**
     * Calcula o valor economizado
     */
    public BigDecimal getValorEconomizado() {
        if (precoOriginal != null && precoAtual != null) {
            return precoOriginal.subtract(precoAtual);
        }
        return BigDecimal.ZERO;
    }
}

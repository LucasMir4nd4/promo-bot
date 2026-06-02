package com.promocoes.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mapeia a resposta de GET https://api.mercadolibre.com/items/{mlbId}
 * Campos desnecessários são ignorados automaticamente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MercadoLivreProdutoDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String titulo;

    @JsonProperty("price")
    private Double precoAtual;

    @JsonProperty("original_price")
    private Double precoOriginal;   // null quando não há desconto ativo

    @JsonProperty("currency_id")
    private String moeda;

    @JsonProperty("condition")
    private String condicao;        // "new" ou "used"

    @JsonProperty("thumbnail")
    private String thumbnail;

    @JsonProperty("pictures")
    private List<Imagem> imagens;

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Retorna a maior imagem disponível ou o thumbnail como fallback.
     */
    public String getImagemUrl() {
        if (imagens != null && !imagens.isEmpty()) {
            return imagens.get(0).getUrl().replace("-I.jpg", "-O.jpg"); // força resolução maior
        }
        return thumbnail;
    }

    /**
     * Percentual de desconto. Retorna 0 se não houver preço original.
     */
    public int getPercentualDesconto() {
        if (precoOriginal == null || precoOriginal == 0) return 0;
        return (int) Math.round(((precoOriginal - precoAtual) / precoOriginal) * 100);
    }

    /**
     * Economia em reais. Retorna 0 se não houver preço original.
     */
    public double getEconomia() {
        if (precoOriginal == null) return 0;
        return precoOriginal - precoAtual;
    }

    /**
     * Preço "de" — usa original_price se existir, senão usa o próprio preço atual.
     */
    public double getPrecoOriginalOuAtual() {
        return precoOriginal != null ? precoOriginal : precoAtual;
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public String getId()         { return id; }
    public String getTitulo()     { return titulo; }
    public Double getPrecoAtual() { return precoAtual; }
    public Double getPrecoOriginal() { return precoOriginal; }
    public String getMoeda()      { return moeda; }
    public String getCondicao()   { return condicao; }
    public String getThumbnail()  { return thumbnail; }

    // ── Inner class ────────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Imagem {
        @JsonProperty("url")
        private String url;
        public String getUrl() { return url; }
    }
}
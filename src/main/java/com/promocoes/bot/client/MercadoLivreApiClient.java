package com.promocoes.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.dto.ProdutoDTO;
import com.promocoes.bot.service.MercadoLivreAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MercadoLivreApiClient {

    private static final String BASE_URL = "https://api.mercadolibre.com";

    private final MercadoLivreAuthService authService;

    @Value("${mercadolivre.partner-tag}")
    private String partnerTag;

    @Value("${mercadolivre.desconto-minimo}")
    private int descontoMinimo;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MercadoLivreApiClient(MercadoLivreAuthService authService) {
        this.authService = authService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Busca produtos em promoção por categoria (highlight da categoria).
     *
     * @param categoriaId ID da categoria no ML (ex: "MLB1276" para Eletrônicos)
     * @param maxResultados número máximo de produtos a retornar
     * @return lista de produtos com desconto e link de afiliado
     */
    public List<ProdutoDTO> buscarPromocoesPorCategoria(String categoriaId, int maxResultados) {
        List<ProdutoDTO> produtos = new ArrayList<>();

        try {
            // 1. Busca os highlights (produtos em destaque/promoção) da categoria
            JsonNode highlights = buscarHighlights(categoriaId);
            JsonNode content = highlights.path("content");

            if (!content.isArray()) {
                log.warn("Nenhum highlight encontrado para categoria {}", categoriaId);
                return produtos;
            }

            int processados = 0;
            for (JsonNode itemNode : content) {
                if (processados >= maxResultados) break;

                String productId = itemNode.path("id").asText();
                if (productId.isBlank()) continue;

                try {
                    ProdutoDTO produto = buscarDadosProduto(productId);
                    if (produto != null) {
                        produtos.add(produto);
                        processados++;
                    }
                } catch (Exception e) {
                    log.warn("Erro ao buscar produto {}: {}", productId, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Erro ao buscar promoções ML para categoria {}: {}", categoriaId, e.getMessage());
        }

        return produtos;
    }

    /**
     * Busca os produtos em destaque (highlights) de uma categoria.
     */
    private JsonNode buscarHighlights(String categoriaId) {
        String url = BASE_URL + "/highlights/MLB/category/" + categoriaId;
        return get(url);
    }

    /**
     * Busca dados completos de um produto pelo ID e monta o DTO com link de afiliado.
     * Retorna null se o produto não atender ao desconto mínimo.
     */
    private ProdutoDTO buscarDadosProduto(String productId) {
        // Busca o item real vinculado ao produto
        JsonNode itemsNode = get(BASE_URL + "/products/" + productId + "/items");
        JsonNode results = itemsNode.path("results");

        if (!results.isArray() || results.isEmpty()) return null;

        JsonNode item = results.get(0);
        String itemId = item.path("item_id").asText();

        // Busca detalhes do produto (título, imagem)
        JsonNode produto = get(BASE_URL + "/products/" + productId);

        String titulo = produto.path("name").asText("Produto sem título");
        String urlImagem = produto.path("pictures").path(0).path("url").asText("");

        // Preços
        BigDecimal precoAtual = BigDecimal.valueOf(item.path("price").asDouble(0));
        BigDecimal precoOriginal = item.path("original_price").isNull()
                ? precoAtual
                : BigDecimal.valueOf(item.path("original_price").asDouble(0));

        if (precoAtual.compareTo(BigDecimal.ZERO) == 0) return null;

        // Calcula desconto
        int desconto = 0;
        if (precoOriginal.compareTo(precoAtual) > 0) {
            desconto = precoOriginal.subtract(precoAtual)
                    .multiply(new BigDecimal("100"))
                    .divide(precoOriginal, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        if (desconto < descontoMinimo) {
            log.debug("Produto {} ignorado: {}% de desconto (mínimo: {}%)", productId, desconto, descontoMinimo);
            return null;
        }

        // Monta link direto e link de afiliado
        String urlProduto = "https://www.mercadolivre.com.br/p/" + productId;
        String urlAfiliado = urlProduto + "?matt_tool=" + partnerTag;

        log.info("Produto encontrado: {} - {}% OFF", titulo, desconto);

        return ProdutoDTO.builder()
                .asin(itemId)              // reutilizando o campo como ID do item ML
                .titulo(titulo)
                .precoAtual(precoAtual)
                .precoOriginal(precoOriginal)
                .percentualDesconto(desconto)
                .urlImagem(urlImagem)
                .urlProduto(urlProduto)
                .urlAfiliado(urlAfiliado)
                .build();
    }


    /**
     * Busca dados de um item direto pelo ID (ex: MLB3939769540).
     * Usado quando o ID vem do links.json — não passa pela busca por categoria.
     */
    public ProdutoDTO buscarPorItemId(String itemId) {
        try {
            JsonNode item = get(BASE_URL + "/products/" + itemId +"/items");

            String titulo = item.path("title").asText("Produto sem título");

            // Pega a maior imagem disponível
            JsonNode pictures = item.path("pictures");
            String urlImagem = pictures.isArray() && !pictures.isEmpty()
                    ? pictures.get(0).path("url").asText("").replace("-I.jpg", "-O.jpg")
                    : item.path("thumbnail").asText("");

            BigDecimal precoAtual = BigDecimal.valueOf(item.path("price").asDouble(0));
            BigDecimal precoOriginal = item.path("original_price").isNull()
                    ? precoAtual
                    : BigDecimal.valueOf(item.path("original_price").asDouble(0));

            int desconto = 0;
            if (precoOriginal.compareTo(precoAtual) > 0) {
                desconto = precoOriginal.subtract(precoAtual)
                        .multiply(new BigDecimal("100"))
                        .divide(precoOriginal, 0, RoundingMode.HALF_UP)
                        .intValue();
            }

            log.info("✅ Item ML: {} | R$ {} | {}% OFF", titulo, precoAtual, desconto);

            return ProdutoDTO.builder()
                    .asin(itemId)
                    .titulo(titulo)
                    .precoAtual(precoAtual)
                    .precoOriginal(precoOriginal)
                    .percentualDesconto(desconto)
                    .urlImagem(urlImagem)
                    .urlProduto("https://www.mercadolivre.com.br/p/" + itemId)
                    .urlAfiliado("") // será substituído pelo link fixo do links.json
                    .build();

        } catch (Exception e) {
            log.error("❌ Erro ao buscar item {}: {}", itemId, e.getMessage());
            throw new RuntimeException("Erro ao buscar item ML: " + itemId, e);
        }
    }

    /**
     * Faz GET autenticado na API do ML e retorna o JSON parseado.
     */
    private JsonNode get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + authService.getAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
        );

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao parsear resposta da API ML: " + url, e);
        }
    }
}
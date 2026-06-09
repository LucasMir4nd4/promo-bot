package com.promocoes.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Client para a API de Afiliados do AliExpress (Open Platform v2).
 *
 * Documentação: https://developers.aliexpress.com/en/doc.htm
 *
 * Para obter credenciais:
 * 1. Crie um app em: https://developers.aliexpress.com/
 * 2. Ative o produto "AliExpress Affiliate"
 * 3. Copie o App Key e App Secret
 * 4. Gere o tracking_id no painel de afiliados: https://portals.aliexpress.com/
 */
@Slf4j
@Component
public class AliexpressApiClient {

    private static final String BASE_URL = "https://api-sg.aliexpress.com/sync";
    private static final String FIELDS = "product_id,product_title,target_sale_price,target_original_price," +
            "original_price,discount,product_main_image_url,promotion_link,product_detail_url," +
            "first_level_category_id,first_level_category_name,commission_rate";

    @Value("${aliexpress.app-key}")
    private String appKey;

    @Value("${aliexpress.app-secret}")
    private String appSecret;

    @Value("${aliexpress.tracking-id}")
    private String trackingId;

    @Value("${aliexpress.desconto-minimo:0}")
    private int descontoMinimo;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AliexpressApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Busca produtos em promoção (hot products) sem filtro de categoria.
     * Ideal para o modo de links automáticos.
     */
    public List<ProdutoDTO> buscarProdutosEmPromocao(int maxResultados) {
        return buscarHotProducts(null, maxResultados);
    }

    /**
     * Busca produtos em promoção filtrados por categoria.
     *
     * @param categoriaId ID da categoria no AliExpress (ex: "509" para Eletrônicos)
     * @param maxResultados número máximo de produtos a retornar
     */
    public List<ProdutoDTO> buscarPorCategoria(String categoriaId, int maxResultados) {
        return buscarHotProducts(categoriaId, maxResultados);
    }

    /**
     * Busca um produto específico pelo ID do AliExpress.
     *
     * @param productId ID do produto (ex: "1005005885373836")
     */
    public ProdutoDTO buscarPorProductId(String productId) {
        try {
            Map<String, String> params = buildBaseParams("aliexpress.affiliate.product.detail.get");
            params.put("product_ids", productId);
            params.put("fields", FIELDS);
            params.put("tracking_id", trackingId);

            JsonNode response = post(params);
            JsonNode respResult = response
                    .path("aliexpress_affiliate_product_detail_get_response")
                    .path("resp_result");

            int respCode = respResult.path("resp_code").asInt(-1);
            if (respCode != 200) {
                log.error("[ALIEXPRESS] Erro ao buscar produto {}: {} - {}",
                        productId, respCode, respResult.path("resp_msg").asText());
                return null;
            }

            JsonNode productList = respResult.path("result").path("products").path("product");
            if (!productList.isArray() || productList.isEmpty()) {
                log.warn("[ALIEXPRESS] Produto não encontrado: {}", productId);
                return null;
            }

            return parseProduct(productList.get(0));

        } catch (Exception e) {
            log.error("[ALIEXPRESS] Erro ao buscar produto {}: {}", productId, e.getMessage());
            throw new RuntimeException("Erro ao buscar produto AliExpress: " + productId, e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────

    private List<ProdutoDTO> buscarHotProducts(String categoriaId, int maxResultados) {
        List<ProdutoDTO> produtos = new ArrayList<>();
        int pagina = 1;
        int pageSize = Math.min(maxResultados, 50);

        try {
            while (produtos.size() < maxResultados) {
                Map<String, String> params = buildBaseParams("aliexpress.affiliate.product.query");
                params.put("fields", FIELDS);
                params.put("tracking_id", trackingId);
                params.put("page_no", String.valueOf(pagina));
                params.put("page_size", String.valueOf(pageSize));

                if (categoriaId != null && !categoriaId.isBlank()) {
                    params.put("category_ids", categoriaId);
                }

                JsonNode response = post(params);
                JsonNode respResult = response
                        .path("aliexpress_affiliate_product_query_response")
                        .path("resp_result");

                int respCode = respResult.path("resp_code").asInt(-1);

                if (respCode != 200) {
                    log.error("[ALIEXPRESS] API retornou erro {}: {}",
                            respCode, respResult.path("resp_msg").asText("erro desconhecido"));
                    break;
                }

                JsonNode result = respResult.path("result");

                JsonNode productList = result.path("products").path("product");
                if (!productList.isArray() || productList.isEmpty()) {
                    log.info("[ALIEXPRESS] Nenhum produto retornado na página {}", pagina);
                    break;
                }

                for (JsonNode node : productList) {
                    if (produtos.size() >= maxResultados) break;
                    ProdutoDTO produto = parseProduct(node);
                    if (produto != null) {
                        produtos.add(produto);
                    }
                }

                long totalRegistros = result.path("total_record_count").asLong(0);
                if (produtos.size() >= maxResultados || (long) pagina * pageSize >= totalRegistros) break;

                pagina++;
            }

        } catch (Exception e) {
            log.error("[ALIEXPRESS] Erro ao buscar hot products: {}", e.getMessage(), e);
        }

        log.info("[ALIEXPRESS] {} produto(s) encontrado(s)", produtos.size());
        return produtos;
    }

    private ProdutoDTO parseProduct(JsonNode node) {
        try {
            String productId   = node.path("product_id").asText("");
            String titulo      = node.path("product_title").asText("Produto sem título");
            String urlImagem   = node.path("product_main_image_url").asText("");
            String urlAfiliado = node.path("promotion_link").asText("");
            String urlProduto  = node.path("product_detail_url").asText("");
            String categoriaId = node.path("first_level_category_id").asText("");

            // target_sale_price e target_original_price já vêm em BRL
            BigDecimal precoAtual    = parseBigDecimal(node.path("target_sale_price").asText("0"));
            BigDecimal precoOriginal = parseBigDecimal(node.path("target_original_price").asText("0"));

            if (precoAtual.compareTo(BigDecimal.ZERO) == 0) return null;

            // desconto já vem pronto ("57%"), mas calculamos se vier vazio
            int desconto;
            String discountStr = node.path("discount").asText("").replace("%", "").trim();
            if (!discountStr.isBlank()) {
                desconto = (int) Double.parseDouble(discountStr);
            } else if (precoOriginal.compareTo(precoAtual) > 0) {
                desconto = precoOriginal.subtract(precoAtual)
                        .multiply(new BigDecimal("100"))
                        .divide(precoOriginal, 0, RoundingMode.HALF_UP)
                        .intValue();
            } else {
                desconto = 0;
            }

            if (desconto < descontoMinimo) {
                log.debug("[ALIEXPRESS] Produto {} ignorado: {}% OFF (mínimo: {}%)",
                        productId, desconto, descontoMinimo);
                return null;
            }

            log.info("[ALIEXPRESS] Produto: {} | {}% OFF | R$ {}", titulo, desconto, precoAtual);

            return ProdutoDTO.builder()
                    .asin(productId)
                    .titulo(titulo)
                    .precoAtual(precoAtual)
                    .precoOriginal(precoOriginal)
                    .percentualDesconto(desconto)
                    .urlImagem(urlImagem)
                    .urlProduto(urlProduto)
                    .urlAfiliado(urlAfiliado)
                    .categoria(categoriaId)
                    .build();

        } catch (Exception e) {
            log.warn("[ALIEXPRESS] Erro ao parsear produto: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> buildBaseParams(String method) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", method);
        params.put("app_key", appKey);
        params.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("sign_method", "hmac-sha256");
        return params;
    }

    private JsonNode post(Map<String, String> params) {
        params.put("sign", assinar(params));

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL);
        params.forEach(builder::queryParam);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.POST,
                new HttpEntity<>(headers), String.class
        );

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao parsear resposta da API AliExpress", e);
        }
    }

    /**
     * Assina a requisição com HMAC-SHA256.
     *
     * Algoritmo:
     * 1. Ordena os parâmetros por chave (alfabético)
     * 2. Concatena como chave+valor sem separador
     * 3. Calcula HMAC-SHA256 usando o appSecret como chave
     * 4. Converte para hex uppercase
     */
    private String assinar(Map<String, String> params) {
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException(
                "ALIEXPRESS_APP_SECRET não configurado. Adicione a variável de ambiente na IDE " +
                "(Run Configuration > Environment Variables) ou rode com o perfil mock.");
        }

        try {
            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);

            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                sb.append(key).append(params.get(key));
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao assinar requisição AliExpress: " + e.getMessage(), e);
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return new BigDecimal(value.replaceAll("[^\\d.]", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

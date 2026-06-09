package com.promocoes.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Client para a Amazon Product Advertising API v5.
 *
 * Documentação: https://webservices.amazon.com.br/paapi5/documentation/
 *
 * IMPORTANTE: Para usar esta API você precisa:
 * 1. Ter conta no Amazon Associates (programa de afiliados)
 * 2. Gerar as credenciais em: https://affiliate-program.amazon.com.br/assoc_credentials/home
 * 3. A conta de afiliado precisa ter pelo menos 3 vendas qualificadas nos primeiros 180 dias
 */
@Slf4j
@Component
public class AmazonApiClient {

    @Value("${amazon.access-key}")
    private String accessKey;

    @Value("${amazon.secret-key}")
    private String secretKey;

    @Value("${amazon.partner-tag}")
    private String partnerTag;

    @Value("${amazon.host}")
    private String host;

    @Value("${amazon.desconto-minimo}")
    private int descontoMinimo;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String SERVICE = "ProductAdvertisingAPI";
    private static final String REGION = "us-east-1";
    private static final String PATH = "/paapi5/searchitems";

    public AmazonApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Busca produtos em promoção por categoria.
     *
     * @param categoria categoria de produtos (ex: "Eletronicos")
     * @param maxResultados número máximo de resultados
     * @return lista de produtos com desconto
     */
    public List<ProdutoDTO> buscarPromocoesPorCategoria(String categoria, int maxResultados) {
        List<ProdutoDTO> produtos = new ArrayList<>();

        try {
            String payload = buildPayload(categoria, maxResultados);
            String url = "https://" + host + PATH;

            HttpHeaders headers = buildHeaders(payload);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                produtos = parseResponse(response.getBody());
            }

        } catch (Exception e) {
            log.error("Erro ao buscar promoções na Amazon para categoria {}: {}", categoria, e.getMessage());
        }

        return produtos;
    }

    /**
     * Monta o payload JSON da requisição para a PA API.
     */
    private String buildPayload(String categoria, int maxResultados) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Keywords", "oferta promoção");
        payload.put("SearchIndex", categoria);
        payload.put("PartnerTag", partnerTag);
        payload.put("PartnerType", "Associates");
        payload.put("Marketplace", "www.amazon.com.br");
        payload.put("ItemCount", maxResultados);

        // Recursos que queremos receber da API
        payload.put("Resources", List.of(
            "ItemInfo.Title",
            "Offers.Listings.Price",
            "Offers.Listings.SavingBasis",
            "Offers.Listings.Promotions",
            "Images.Primary.Large",
            "ItemInfo.ByLineInfo"
        ));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao serializar payload Amazon", e);
        }
    }

    /**
     * Constrói os headers com autenticação AWS Signature Version 4.
     */
    private HttpHeaders buildHeaders(String payload) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Hash do payload
        String payloadHash = sha256Hex(payload);

        // Headers canônicos
        String canonicalHeaders =
            "content-encoding:amz-1.0\n" +
            "content-type:application/json; charset=utf-8\n" +
            "host:" + host + "\n" +
            "x-amz-date:" + amzDate + "\n" +
            "x-amz-target:com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems\n";

        String signedHeaders = "content-encoding;content-type;host;x-amz-date;x-amz-target";

        String canonicalRequest =
            "POST\n" + PATH + "\n\n" +
            canonicalHeaders + "\n" +
            signedHeaders + "\n" +
            payloadHash;

        // String to sign
        String credentialScope = dateStamp + "/" + REGION + "/" + SERVICE + "/aws4_request";
        String stringToSign =
            "AWS4-HMAC-SHA256\n" +
            amzDate + "\n" +
            credentialScope + "\n" +
            sha256Hex(canonicalRequest);

        // Assinatura
        byte[] signingKey = getSignatureKey(secretKey, dateStamp, REGION, SERVICE);
        String signature = hmacSHA256Hex(stringToSign, signingKey);

        String authorizationHeader =
            "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope +
            ", SignedHeaders=" + signedHeaders +
            ", Signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Encoding", "amz-1.0");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Host", host);
        headers.set("X-Amz-Date", amzDate);
        headers.set("X-Amz-Target", "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems");
        headers.set("Authorization", authorizationHeader);

        return headers;
    }

    /**
     * Faz o parse do JSON de resposta da Amazon e extrai os produtos com desconto.
     */
    private List<ProdutoDTO> parseResponse(String json) throws Exception {
        List<ProdutoDTO> produtos = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);

        JsonNode items = root.path("SearchResult").path("Items");
        if (!items.isArray()) return produtos;

        for (JsonNode item : items) {
            try {
                String asin = item.path("ASIN").asText();
                String titulo = item.path("ItemInfo").path("Title").path("DisplayValue").asText();

                JsonNode listings = item.path("Offers").path("Listings");
                if (!listings.isArray() || listings.isEmpty()) continue;

                JsonNode listing = listings.get(0);
                BigDecimal precoAtual = new BigDecimal(
                    listing.path("Price").path("Amount").asText("0")
                );
                BigDecimal precoOriginal = new BigDecimal(
                    listing.path("SavingBasis").path("Amount").asText("0")
                );

                // Ignora se não tiver preço de referência para calcular desconto
                if (precoOriginal.compareTo(BigDecimal.ZERO) == 0) continue;

                int desconto = precoOriginal.subtract(precoAtual)
                    .multiply(new BigDecimal("100"))
                    .divide(precoOriginal, 0, java.math.RoundingMode.HALF_UP)
                    .intValue();

                // Filtra pelo desconto mínimo configurado
                if (desconto < descontoMinimo) continue;

                String urlImagem = item.path("Images").path("Primary").path("Large").path("URL").asText();
                String urlProduto = "https://www.amazon.com.br/dp/" + asin;
                String urlAfiliado = urlProduto + "?tag=" + partnerTag;

                ProdutoDTO dto = ProdutoDTO.builder()
                    .asin(asin)
                    .titulo(titulo)
                    .precoAtual(precoAtual)
                    .precoOriginal(precoOriginal)
                    .percentualDesconto(desconto)
                    .urlImagem(urlImagem)
                    .urlProduto(urlProduto)
                    .urlAfiliado(urlAfiliado)
                    .fonte("Amazon")
                    .build();

                produtos.add(dto);
                log.info("Produto encontrado: {} - {}% OFF", titulo, desconto);

            } catch (Exception e) {
                log.warn("Erro ao parsear item da Amazon: {}", e.getMessage());
            }
        }

        return produtos;
    }

    // ─── Utilitários de criptografia AWS Signature v4 ───────────────────────

    private String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String hmacSHA256Hex(String data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return bytesToHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmacSHA256Bytes(String data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] getSignatureKey(String key, String date, String region, String service) throws Exception {
        byte[] kDate    = hmacSHA256Bytes(date,              ("AWS4" + key).getBytes(StandardCharsets.UTF_8));
        byte[] kRegion  = hmacSHA256Bytes(region,             kDate);
        byte[] kService = hmacSHA256Bytes(service,            kRegion);
        return                hmacSHA256Bytes("aws4_request", kService);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

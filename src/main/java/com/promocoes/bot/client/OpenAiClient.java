package com.promocoes.bot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Client para a API da OpenAI.
 * Gera headline chamativa e texto de venda para cada promoção.
 */
@Slf4j
@Component
public class OpenAiClient {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens}")
    private int maxTokens;

    @Value("${openai.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Gera um texto de venda persuasivo para o produto em promoção.
     *
     * @param produto dados do produto com desconto
     * @return CopyPromoDTO com headline e texto de venda
     */
    public CopyPromoDTO gerarCopyPromocional(ProdutoDTO produto) {
        try {
            String prompt = buildPrompt(produto);
            String resposta = chamarOpenAi(prompt);
            return parsearResposta(resposta);

        } catch (Exception e) {
            log.error("Erro ao gerar copy via OpenAI para {}: {}", produto.getTitulo(), e.getMessage());
            // Fallback: retorna um texto padrão se a IA falhar
            return CopyPromoDTO.builder()
                .headline("🔥 OFERTA IMPERDÍVEL!")
                .textoVenda(produto.getTitulo() + " com " + produto.getPercentualDesconto() + "% de desconto!")
                .emoji("🛒")
                .build();
        }
    }

    /**
     * Monta o prompt enviado para o ChatGPT.
     */
    private String buildPrompt(ProdutoDTO produto) {
        return String.format("""
            Você é um especialista em marketing de afiliados. Crie um texto de promoção para WhatsApp e Telegram.
            
            Produto: %s
            Preço original: R$ %.2f
            Preço com desconto: R$ %.2f
            Desconto: %d%%
            Economia: R$ %.2f
            
            Retorne APENAS um JSON válido neste formato exato (sem markdown, sem explicações):
            {
              "headline": "headline chamativa com emoji no início (max 80 chars)",
              "textoVenda": "texto persuasivo de 2-3 linhas mencionando o desconto e urgência",
              "emoji": "emoji temático do produto"
            }
            
            Regras:
            - Use linguagem informal e empolgante
            - Mencione o percentual de desconto
            - Crie senso de urgência
            - Máximo 3 linhas no textoVenda
            - Escreva em português brasileiro
            """,
            produto.getTitulo(),
            produto.getPrecoOriginal(),
            produto.getPrecoAtual(),
            produto.getPercentualDesconto(),
            produto.getValorEconomizado()
        );
    }

    /**
     * Faz a chamada para a API da OpenAI.
     */
    private String chamarOpenAi(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> mensagem = Map.of(
            "role", "user",
            "content", prompt
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(mensagem));
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.8); // Criatividade moderada

        String payload = objectMapper.writeValueAsString(body);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl, HttpMethod.POST, request, String.class
        );

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("OpenAI retornou status: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    /**
     * Faz o parse do JSON retornado pela OpenAI.
     */
    private CopyPromoDTO parsearResposta(String resposta) throws Exception {
        // Remove possíveis blocos de código markdown
        String json = resposta
            .replaceAll("```json", "")
            .replaceAll("```", "")
            .trim();

        JsonNode node = objectMapper.readTree(json);

        return CopyPromoDTO.builder()
            .headline(node.path("headline").asText())
            .textoVenda(node.path("textoVenda").asText())
            .emoji(node.path("emoji").asText("🛒"))
            .build();
    }
}

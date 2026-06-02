package com.promocoes.bot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client para a Evolution API (WhatsApp).
 *
 * Como configurar:
 * 1. Suba a Evolution API via Docker (veja docker-compose.yml)
 * 2. Acesse o painel: http://localhost:8080
 * 3. Crie uma instância e escaneie o QR Code com seu WhatsApp
 * 4. Preencha as variáveis EVOLUTION_API_URL, EVOLUTION_API_KEY, EVOLUTION_INSTANCE no .env
 * 5. Adicione os IDs dos grupos em WHATSAPP_GRUPOS
 *
 * Para pegar o ID de um grupo:
 * GET http://localhost:8080/group/fetchAllGroups/{instance}?getParticipants=false
 */
@Slf4j
@Component
public class WhatsAppEvolutionClient {

    @Value("${whatsapp.evolution-api-url}")
    private String evolutionApiUrl;

    @Value("${whatsapp.evolution-api-key}")
    private String evolutionApiKey;

    @Value("${whatsapp.instance-name}")
    private String instanceName;

    @Value("${whatsapp.grupos}")
    private String gruposConfig;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WhatsAppEvolutionClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Envia a promoção para todos os grupos configurados.
     *
     * @return true se ao menos um envio foi bem-sucedido
     */
    public boolean enviarPromocao(ProdutoDTO produto, CopyPromoDTO copy) {
        List<String> grupos = Arrays.asList(gruposConfig.split(","));
        boolean algumSucesso = false;

        for (String grupoId : grupos) {
            String id = grupoId.trim();
            try {
                // 1. Envia a imagem primeiro
                if (produto.getUrlImagem() != null && !produto.getUrlImagem().isBlank()) {
                    enviarImagem(id, produto.getUrlImagem(), produto.getTitulo());
                    Thread.sleep(500); // Pequeno delay entre mensagens
                }

                // 2. Envia o texto formatado
                String mensagem = formatarMensagem(produto, copy);
                enviarTexto(id, mensagem);

                log.info("[WHATSAPP] Promoção enviada para grupo {}: {}", id, produto.getTitulo());
                algumSucesso = true;

            } catch (Exception e) {
                log.error("[WHATSAPP] Erro ao enviar para grupo {}: {}", id, e.getMessage());
            }
        }

        return algumSucesso;
    }

    /**
     * Envia uma mensagem de texto simples para um grupo.
     */
    private void enviarTexto(String grupoId, String texto) {
        String url = evolutionApiUrl + "/message/sendText/" + instanceName;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", grupoId);
        body.put("text", texto);

        fazerRequisicao(url, body);
    }

    /**
     * Envia uma imagem com legenda para um grupo.
     */
    private void enviarImagem(String grupoId, String urlImagem, String legenda) {
        String url = evolutionApiUrl + "/message/sendMedia/" + instanceName;

        Map<String, Object> mediaMessage = new LinkedHashMap<>();
        mediaMessage.put("mediatype", "image");
        mediaMessage.put("mimetype", "image/jpeg");
        mediaMessage.put("caption", legenda);
        mediaMessage.put("media", urlImagem);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", grupoId);
        body.put("mediaMessage", mediaMessage);

        fazerRequisicao(url, body);
    }

    /**
     * Executa a chamada HTTP para a Evolution API.
     */
    private void fazerRequisicao(String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", evolutionApiKey);

            String payload = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Status HTTP: " + response.getStatusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("Falha na Evolution API: " + e.getMessage(), e);
        }
    }

    /**
     * Formata a mensagem para WhatsApp (usa marcação do WhatsApp: *negrito*, _itálico_).
     */
    private String formatarMensagem(ProdutoDTO produto, CopyPromoDTO copy) {
        return String.format(
            "*%s*\n\n" +
            "%s\n\n" +
            "💰 ~R$ %.2f~ → *R$ %.2f*\n" +
            "📉 *%d%% OFF* | Economia de *R$ %.2f*\n\n" +
            "👇 GARANTA O SEU AGORA:\n%s",
            copy.getHeadline(),
            copy.getTextoVenda(),
            produto.getPrecoOriginal(),
            produto.getPrecoAtual(),
            produto.getPercentualDesconto(),
            produto.getValorEconomizado(),
            produto.getUrlAfiliado()
        );
    }
}

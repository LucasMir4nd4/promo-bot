package com.promocoes.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Gerencia o ciclo de vida do access token do Mercado Livre.
 *
 * O token expira em 6 horas (21600s). O refresh token é rotativo:
 * cada vez que ele é usado, a API devolve um novo refresh token.
 *
 * Fluxo inicial:
 * 1. Gere o primeiro par access_token/refresh_token manualmente via OAuth2:
 *    POST https://api.mercadolibre.com/oauth/token
 *    grant_type=authorization_code&client_id=X&client_secret=Y&code=Z&redirect_uri=W
 * 2. Coloque o refresh_token no .env (ML_REFRESH_TOKEN)
 * 3. A partir daí este serviço renova automaticamente.
 */
@Slf4j
@Service
public class MercadoLivreAuthService {

    private static final String TOKEN_URL = "https://api.mercadolibre.com/oauth/token";
    private static final long MARGEM_RENOVACAO_SEGUNDOS = 300; // renova 5 min antes de expirar

    @Value("${mercadolivre.client-id}")
    private String clientId;

    @Value("${mercadolivre.client-secret}")
    private String clientSecret;

    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile Instant expiresAt;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MercadoLivreAuthService(
            @Value("${mercadolivre.access-token:}") String accessToken,
            @Value("${mercadolivre.refresh-token:}") String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        // Se já existe um access token no .env, assume que é válido por enquanto.
        // Se estiver em branco, isExpirado() retornará true e o primeiro getAccessToken() o renova.
        this.expiresAt = accessToken.isBlank() ? Instant.EPOCH : Instant.now().plusSeconds(21600);
    }

    /**
     * Retorna o access token atual, renovando-o se estiver próximo do vencimento.
     * Thread-safe: chamadas concorrentes não causam múltiplos refreshes simultâneos.
     */
    public String getAccessToken() {
        if (isProximoDeExpirar()) {
            renovar();
        }
        return accessToken;
    }

    /**
     * Refresh agendado: roda a cada hora e renova se estiver dentro da margem.
     * Garante que o token nunca expire entre ciclos do scheduler de promoções.
     */
    @Scheduled(fixedDelayString = "${mercadolivre.token-check-intervalo-ms:3600000}")
    public void renovacaoAgendada() {
        if (isProximoDeExpirar()) {
            log.info("[ML AUTH] Renovação agendada iniciada...");
            renovar();
        }
    }

    // ─────────────────────────────────────────────────────────────

    private synchronized void renovar() {
        if (!isProximoDeExpirar()) return; // outro thread já renovou

        if (refreshToken == null || refreshToken.isBlank()) {
            log.error("[ML AUTH] Refresh token não configurado. Defina ML_REFRESH_TOKEN no .env.");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "grant_type=refresh_token"
                    + "&client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&refresh_token=" + refreshToken;

            ResponseEntity<String> response = restTemplate.exchange(
                    TOKEN_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class
            );

            JsonNode json = objectMapper.readTree(response.getBody());

            this.accessToken  = json.path("access_token").asText();
            this.refreshToken = json.path("refresh_token").asText(this.refreshToken); // ML rotaciona o refresh token
            long expiresIn    = json.path("expires_in").asLong(21600);
            this.expiresAt    = Instant.now().plusSeconds(expiresIn);

            log.info("[ML AUTH] Token renovado com sucesso. Expira em: {}", expiresAt);

        } catch (Exception e) {
            log.error("[ML AUTH] Falha ao renovar token: {}. Nova tentativa em 60s.", e.getMessage());
            this.expiresAt = Instant.now().plusSeconds(60); // tenta de novo em breve
        }
    }

    private boolean isProximoDeExpirar() {
        return expiresAt == null
                || Instant.now().isAfter(expiresAt.minusSeconds(MARGEM_RENOVACAO_SEGUNDOS));
    }
}

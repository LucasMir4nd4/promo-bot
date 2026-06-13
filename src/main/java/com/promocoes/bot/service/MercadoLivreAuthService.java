package com.promocoes.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.model.ConfigEntry;
import com.promocoes.bot.repository.ConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia o ciclo de vida do access token do Mercado Livre.
 *
 * Fluxo completo:
 *   1. GET /api/mercadolivre/auth         → redireciona o usuário ao ML (PKCE + state)
 *   2. GET /api/mercadolivre/callback     → ML devolve ?code=X&state=Y → exchangeCodeForToken()
 *   3. Refresh automático agendado (a cada hora) → renovar()
 *
 * O refresh token é rotativo: cada uso devolve um novo token.
 * O token mais recente é persistido na tabela `config` para sobreviver a restarts.
 */
@Slf4j
@Service
public class MercadoLivreAuthService {

    private static final String TOKEN_URL              = "https://api.mercadolibre.com/oauth/token";
    private static final String AUTH_URL               = "https://auth.mercadolivre.com.br/authorization";
    private static final String CONFIG_KEY_REFRESH     = "ml_refresh_token";
    private static final long   MARGEM_RENOVACAO_S     = 300;   // renova 5 min antes de expirar
    private static final long   STATE_TTL_S            = 600;   // state válido por 10 min

    @Value("${mercadolivre.client-id}")
    private String clientId;

    @Value("${mercadolivre.client-secret}")
    private String clientSecret;

    @Value("${mercadolivre.redirect-uri:http://localhost:8081/api/mercadolivre/callback}")
    private String redirectUri;

    private volatile String  accessToken;
    private volatile String  refreshToken;
    private volatile Instant expiresAt;

    /** state gerado → {codeVerifier, timestamp} — limpo após uso ou expiração */
    private final Map<String, PkceEntry> pendingStates = new ConcurrentHashMap<>();

    private final ConfigRepository configRepository;
    private final RestTemplate     restTemplate  = new RestTemplate();
    private final ObjectMapper     objectMapper  = new ObjectMapper();
    private final SecureRandom     secureRandom  = new SecureRandom();

    public MercadoLivreAuthService(
            @Value("${mercadolivre.access-token:}")  String accessToken,
            @Value("${mercadolivre.refresh-token:}") String refreshToken,
            ConfigRepository configRepository) {
        this.accessToken    = accessToken;
        this.refreshToken   = refreshToken;
        this.configRepository = configRepository;
        this.expiresAt = accessToken.isBlank() ? Instant.EPOCH : Instant.now().plusSeconds(21600);
    }

    /** Ao iniciar, o token do banco (mais recente) sobrescreve o do application.yml. */
    @PostConstruct
    public void carregarTokenDoBanco() {
        configRepository.findById(CONFIG_KEY_REFRESH).ifPresent(entry -> {
            log.info("[ML AUTH] Refresh token carregado do banco.");
            this.refreshToken = entry.getValor();
        });
    }

    // ─── Etapa 1: URL de autorização ──────────────────────────────────────────

    /**
     * Gera a URL de autorização OAuth2 com PKCE (S256) e state aleatório.
     * Redirecione o usuário para esta URL para iniciar o fluxo.
     */
    public String buildAuthorizationUrl() {
        String state         = gerarState();
        String codeVerifier  = gerarCodeVerifier();
        String codeChallenge = gerarCodeChallenge(codeVerifier);

        pendingStates.put(state, new PkceEntry(codeVerifier, Instant.now()));

        return AUTH_URL
                + "?response_type=code"
                + "&client_id="             + clientId
                + "&redirect_uri="          + encode(redirectUri)
                + "&state="                 + state
                + "&code_challenge="        + codeChallenge
                + "&code_challenge_method=S256";
    }

    // ─── Etapa 2: Troca do code por tokens ────────────────────────────────────

    /**
     * Troca o authorization_code recebido no callback por access_token + refresh_token.
     * Valida o state para prevenir CSRF. Persiste o refresh_token no banco.
     *
     * @return true se os tokens foram obtidos com sucesso
     */
    public boolean exchangeCodeForToken(String code, String state) {
        PkceEntry pkce = pendingStates.remove(state);

        if (pkce == null) {
            log.warn("[ML AUTH] State inválido ou não encontrado: {}", state);
            return false;
        }
        if (Instant.now().isAfter(pkce.createdAt().plusSeconds(STATE_TTL_S))) {
            log.warn("[ML AUTH] State expirado: {}", state);
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "grant_type=authorization_code"
                    + "&client_id="      + clientId
                    + "&client_secret="  + clientSecret
                    + "&code="           + code
                    + "&redirect_uri="   + encode(redirectUri)
                    + "&code_verifier="  + pkce.codeVerifier();

            ResponseEntity<String> response = restTemplate.exchange(
                    TOKEN_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class
            );

            salvarTokensDaResposta(response.getBody());
            log.info("[ML AUTH] Tokens obtidos via authorization_code. Expira em: {}", expiresAt);
            return true;

        } catch (Exception e) {
            log.error("[ML AUTH] Falha ao trocar code por token: {}", e.getMessage());
            return false;
        }
    }

    // ─── Token público ────────────────────────────────────────────────────────

    /** Retorna o access token atual, renovando-o automaticamente se necessário. */
    public String getAccessToken() {
        if (isProximoDeExpirar()) {
            renovar();
        }
        return accessToken;
    }

    /** True se existe um access token válido (não expirado). */
    public boolean hasValidToken() {
        return accessToken != null
                && !accessToken.isBlank()
                && expiresAt != null
                && Instant.now().isBefore(expiresAt);
    }

    // ─── Etapa 3: Renovação agendada ─────────────────────────────────────────

    @Scheduled(fixedDelayString = "${mercadolivre.token-check-intervalo-ms:3600000}")
    public void renovacaoAgendada() {
        if (isProximoDeExpirar()) {
            log.info("[ML AUTH] Renovação agendada iniciada...");
            renovar();
        }
    }

    // ─── Internos ─────────────────────────────────────────────────────────────

    private synchronized void renovar() {
        if (!isProximoDeExpirar()) return;

        if (refreshToken == null || refreshToken.isBlank()) {
            log.error("[ML AUTH] Sem refresh token. Inicie o fluxo OAuth2 via GET /api/mercadolivre/auth");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "grant_type=refresh_token"
                    + "&client_id="     + clientId
                    + "&client_secret=" + clientSecret
                    + "&refresh_token=" + refreshToken;

            ResponseEntity<String> response = restTemplate.exchange(
                    TOKEN_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class
            );

            salvarTokensDaResposta(response.getBody());
            log.info("[ML AUTH] Token renovado com sucesso. Expira em: {}", expiresAt);

        } catch (Exception e) {
            log.error("[ML AUTH] Falha ao renovar token: {}. Nova tentativa em 60s.", e.getMessage());
            this.expiresAt = Instant.now().plusSeconds(60);
        }
    }

    private void salvarTokensDaResposta(String responseBody) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);

        this.accessToken = json.path("access_token").asText();
        long expiresIn   = json.path("expires_in").asLong(21600);
        this.expiresAt   = Instant.now().plusSeconds(expiresIn);

        String novoRefresh = json.path("refresh_token").asText("");
        if (!novoRefresh.isBlank()) {
            this.refreshToken = novoRefresh;
            persistirRefreshToken(novoRefresh);
        }
    }

    private void persistirRefreshToken(String token) {
        try {
            configRepository.save(new ConfigEntry(CONFIG_KEY_REFRESH, token));
            log.debug("[ML AUTH] Refresh token persistido no banco.");
        } catch (Exception e) {
            log.error("[ML AUTH] Falha ao persistir refresh token: {}", e.getMessage());
        }
    }

    private boolean isProximoDeExpirar() {
        return expiresAt == null
                || Instant.now().isAfter(expiresAt.minusSeconds(MARGEM_RENOVACAO_S));
    }

    // ─── PKCE + State ─────────────────────────────────────────────────────────

    private String gerarState() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String gerarCodeVerifier() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String gerarCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record PkceEntry(String codeVerifier, Instant createdAt) {}
}

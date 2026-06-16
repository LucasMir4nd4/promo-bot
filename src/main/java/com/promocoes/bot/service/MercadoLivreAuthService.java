package com.promocoes.bot.service;

import com.promocoes.bot.config.MercadoLivreAuthConfig;
import com.promocoes.bot.dto.MercadoLivreTokenResponseDTO;
import com.promocoes.bot.model.MercadoLivreToken;
import com.promocoes.bot.repository.MercadoLivreTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * AuthService do OAuth 2.0 do Mercado Livre.
 *
 * Fluxo geral pra um bot server-side:
 *   1) UMA vez, na mão: abrir a URL de autorização (getUrlAutorizacao), autorizar
 *      logado como ADMIN da conta, e capturar o ?code= do redirect.
 *   2) Trocar esse code por tokens (trocarCodePorToken) -> salva no banco.
 *   3) Daí pra frente o bot só chama getAccessTokenValido(), que renova sozinho
 *      usando o refresh_token rotativo. Nunca mais precisa de interação humana
 *      (desde que rode ao menos 1x a cada 6 meses).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoLivreAuthService {

    private static final Long TOKEN_ID = 1L;

    private final MercadoLivreAuthConfig config;
    private final MercadoLivreTokenRepository tokenRepository;
    private final RestTemplate restTemplate;

    /**
     * Monta a URL que você abre no navegador pra autorizar o app (passo manual, 1x).
     * O 'state' é seu — gere um valor aleatório e confira na volta.
     */
    public String getUrlAutorizacao(String state) {
        return config.getAuthUrl()
                + "?response_type=code"
                + "&client_id=" + config.getClientId()
                + "&redirect_uri=" + config.getRedirectUri()
                + "&state=" + state;
    }

    /**
     * Passo 2: troca o authorization_code (uso único, expira rápido) por tokens.
     * Chame isso a partir do callback do redirect_uri.
     */
    public MercadoLivreToken trocarCodePorToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("code", code);
        body.add("redirect_uri", config.getRedirectUri());

        log.info("Trocando authorization_code por access/refresh token...");
        MercadoLivreTokenResponseDTO resp = postToken(body);
        return salvar(resp);
    }

    /**
     * Devolve um access_token garantidamente válido.
     * Renova automaticamente se estiver expirado (ou perto disso).
     * É ESTE método que o resto do bot deve usar.
     */
    public synchronized String getAccessTokenValido() {
        MercadoLivreToken token = tokenRepository.findById(TOKEN_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum token do Mercado Livre salvo. Rode o fluxo de autorização manual primeiro."));

        if (token.expiradoCom(config.getBufferRenovacaoSegundos())) {
            log.info("Access token do ML expirado/expirando. Renovando...");
            token = renovarToken(token);
        }
        return token.getAccessToken();
    }

    /**
     * Renovação com ROTAÇÃO: o ML devolve um refresh_token NOVO e invalida o antigo.
     * O novo é salvo imediatamente — perder isso = ter que refazer o fluxo manual.
     */
    public MercadoLivreToken renovarToken(MercadoLivreToken atual) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("refresh_token", atual.getRefreshToken());

        MercadoLivreTokenResponseDTO resp = postToken(body);
        return salvar(resp);
    }

    // ---------- internos ----------

    private MercadoLivreTokenResponseDTO postToken(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<MercadoLivreTokenResponseDTO> response = restTemplate.postForEntity(
                    config.getTokenUrl(), request, MercadoLivreTokenResponseDTO.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            // invalid_grant aqui geralmente = refresh_token já usado/expirado/revogado.
            log.error("Erro OAuth ML [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Falha no OAuth do Mercado Livre", e);
        }
    }

    private MercadoLivreToken salvar(MercadoLivreTokenResponseDTO resp) {
        if (resp == null || resp.getAccessToken() == null) {
            throw new IllegalStateException("Resposta de token do ML veio vazia/inválida.");
        }
        MercadoLivreToken token = MercadoLivreToken.builder()
                .id(TOKEN_ID)
                .accessToken(resp.getAccessToken())
                .refreshToken(resp.getRefreshToken())
                .userId(resp.getUserId())
                .scope(resp.getScope())
                .expiraEm(LocalDateTime.now().plusSeconds(resp.getExpiresIn()))
                .build();

        MercadoLivreToken salvo = tokenRepository.save(token);
        log.info("Token ML salvo. user_id={}, expira em {}", salvo.getUserId(), salvo.getExpiraEm());
        return salvo;
    }
}

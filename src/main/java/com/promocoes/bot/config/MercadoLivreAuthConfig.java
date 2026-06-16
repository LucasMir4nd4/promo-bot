package com.promocoes.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Credenciais e URLs do OAuth 2.0 do Mercado Livre.
 * Preenchido via application.yml -> mercadolivre.auth.*
 * (que por sua vez lê das env vars, nunca hardcode).
 */
@Configuration
@ConfigurationProperties(prefix = "mercadolivre.auth")
@Data
public class MercadoLivreAuthConfig {

    /** APP ID do aplicativo criado no painel do ML. */
    private String clientId;

    /** Secret Key gerada ao criar o aplicativo. */
    private String clientSecret;

    /** Tem que ser IDÊNTICO ao cadastrado no app (sem parâmetro variável). */
    private String redirectUri;

    /** Endpoint de troca/renovação de token. */
    private String tokenUrl = "https://api.mercadolibre.com/oauth/token";

    /** Base de autorização (BR). Troque o domínio pra outro país se precisar. */
    private String authUrl = "https://auth.mercadolivre.com.br/authorization";

    /** Renova o access token quando faltar menos que isso pra expirar (segundos). */
    private long bufferRenovacaoSegundos = 600; // 10 min de folga
}

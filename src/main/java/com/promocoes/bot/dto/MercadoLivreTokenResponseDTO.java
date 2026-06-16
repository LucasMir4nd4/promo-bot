package com.promocoes.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Espelha a resposta do POST https://api.mercadolibre.com/oauth/token
 * tanto pro grant_type=authorization_code quanto pro refresh_token.
 */
@Data
public class MercadoLivreTokenResponseDTO {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn; // segundos (21600 = 6h)

    private String scope;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("refresh_token")
    private String refreshToken;
}

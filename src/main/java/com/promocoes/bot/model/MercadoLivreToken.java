package com.promocoes.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Guarda o par access/refresh token do Mercado Livre.
 *
 * IMPORTANTE: o refresh_token é ROTATIVO e de uso único — a cada renovação o ML
 * devolve um novo e invalida o anterior. Por isso ele PRECISA ser persistido:
 * se o bot reiniciar com um refresh_token antigo, o acesso trava e você tem que
 * refazer o fluxo manual de autorização.
 *
 * Mantemos sempre 1 linha (id = 1L) sobrescrevendo a anterior.
 */
@Entity
@Table(name = "ml_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoLivreToken {

    @Id
    private Long id;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "user_id")
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String scope;

    /** Momento exato em que o access_token deixa de valer. */
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    public void preSave() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public boolean expiradoCom(long bufferSegundos) {
        return LocalDateTime.now().isAfter(expiraEm.minusSeconds(bufferSegundos));
    }
}

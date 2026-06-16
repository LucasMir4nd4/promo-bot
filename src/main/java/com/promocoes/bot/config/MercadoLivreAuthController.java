package com.promocoes.bot.config;

import com.promocoes.bot.model.MercadoLivreToken;
import com.promocoes.bot.service.MercadoLivreAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * Endpoints só pra fazer o bootstrap manual do OAuth (1x).
 * Depois disso o bot se vira sozinho e você nem usa mais isso.
 *
 * Passo a passo:
 *   1) GET /api/ml/auth/login  -> abre a URL retornada no navegador (logado como ADMIN)
 *   2) autoriza -> o ML redireciona pro redirect_uri com ?code=...
 *      (configure o redirect_uri do app apontando pra /api/ml/auth/callback)
 *   3) o callback troca o code por tokens e salva no banco
 */
@RestController
@RequestMapping("/api/ml/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MercadoLivreAuthController {

    private final MercadoLivreAuthService authService;
    private final SecureRandom random = new SecureRandom();

    @GetMapping("/login")
    public Map<String, String> login() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Em produção guarde o state (sessão/cache) e confira no callback.
        return Map.of("urlAutorizacao", authService.getUrlAutorizacao(state), "state", state);
    }

    @GetMapping("/callback")
    public Map<String, Object> callback(@RequestParam String code,
                                        @RequestParam(required = false) String state) {
        MercadoLivreToken token = authService.trocarCodePorToken(code);
        return Map.of(
                "status", "ok",
                "userId", token.getUserId(),
                "scope", token.getScope(),
                "expiraEm", token.getExpiraEm().toString());
    }

    /**
     * Bootstrap alternativo: quando a troca do code é feita FORA do bot
     * (ex: Postman, via redirect_uri https://oauth.pstmn.io/v1/callback),
     * cole aqui o refresh_token obtido. O bot troca por um par novo na hora
     * e salva no banco — daí pra frente renova sozinho.
     *
     * Body JSON: { "refreshToken": "TG-..." }
     */
    @PostMapping("/seed")
    public Map<String, Object> seed(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Map.of("status", "erro", "mensagem", "Campo 'refreshToken' obrigatório.");
        }
        MercadoLivreToken transiente = MercadoLivreToken.builder()
                .id(1L)
                .accessToken("PENDING")
                .refreshToken(refreshToken)
                .expiraEm(LocalDateTime.now())
                .build();
        MercadoLivreToken salvo = authService.renovarToken(transiente);
        return Map.of(
                "status", "ok",
                "userId", salvo.getUserId(),
                "scope", salvo.getScope(),
                "expiraEm", salvo.getExpiraEm().toString());
    }
}

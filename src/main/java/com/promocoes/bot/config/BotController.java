package com.promocoes.bot.config;

import com.promocoes.bot.repository.ProdutoEnviadoRepository;
import com.promocoes.bot.service.AliexpressPromoService;
import com.promocoes.bot.service.MercadoLivreAuthService;
import com.promocoes.bot.service.MercadoLivrePromoService;
import com.promocoes.bot.service.AmazonPromoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller REST com endpoints utilitários.
 *
 * Permite disparar o ciclo manualmente e consultar estatísticas.
 * Útil para testes e monitoramento via VPS.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BotController {

    private final MercadoLivrePromoService mercadoLivrePromoService;
    private final MercadoLivreAuthService  mercadoLivreAuthService;
    private final AmazonPromoService       amazonPromoService;
    private final AliexpressPromoService   aliexpressService;
    private final ProdutoEnviadoRepository repository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        long totalEnviados = repository.count();
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now().toString(),
            "totalProdutosEnviados", totalEnviados
        ));
    }


    // ─── OAuth2 Mercado Livre ─────────────────────────────────────────────────

    /** Etapa 1: redireciona o navegador para a página de autorização do ML. */
    @GetMapping("/mercadolivre/auth")
    public ResponseEntity<Void> iniciarAuth() {
        String url = mercadoLivreAuthService.buildAuthorizationUrl();
        log.info("[AUTH] Redirecionando para ML: {}", url);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    /** Etapa 2: callback OAuth2 — ML redireciona aqui com ?code=X&state=Y. */
    @GetMapping("/mercadolivre/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam String code,
            @RequestParam String state) {

        boolean ok = mercadoLivreAuthService.exchangeCodeForToken(code, state);
        if (ok) {
            log.info("[AUTH] Autorização ML concluída com sucesso.");
            return ResponseEntity.ok(Map.of("status", "autorizado", "mensagem", "Tokens obtidos e salvos com sucesso."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "erro", "mensagem", "Falha ao trocar code por token. Verifique os logs."));
    }

    /** Status do token atual. */
    @GetMapping("/mercadolivre/auth/status")
    public ResponseEntity<Map<String, Object>> statusToken() {
        return ResponseEntity.ok(Map.of(
                "tokenValido", mercadoLivreAuthService.hasValidToken()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/mercadolivre/executar")
    public ResponseEntity<Map<String, String>> executarManual() {
        log.info("[API] Execução manual solicitada");
        new Thread(mercadoLivrePromoService::processarPromocoes).start();
        return ResponseEntity.ok(Map.of(
            "mensagem", "Ciclo de promoções iniciado em background",
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/amazon/executar")
    public ResponseEntity<Map<String, String>> executarAmazon() {
        log.info("[API] Execução manual solicitada");
        new Thread(amazonPromoService::processarPromocoes).start();
        return ResponseEntity.ok(Map.of(
            "mensagem", "Ciclo de promoções iniciado em background",
            "timestamp", LocalDateTime.now().toString()
        ));
    }
    
    @PostMapping("/mercadolivre/buscarcategorias")
    public ResponseEntity<?> executar() {
        return ResponseEntity.ok(mercadoLivrePromoService.buscarCategorias());
    }



    @PostMapping("/aliexpress/executar")
    public ResponseEntity<Map<String, String>> executarAliexpress() {
        log.info("[API] Execução manual AliExpress solicitada");
        new Thread(aliexpressService::processarPromocoes).start();
        return ResponseEntity.ok(Map.of(
                "mensagem", "Ciclo AliExpress iniciado em background",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Lista os últimos produtos enviados.
     * GET /api/enviados?horas=24
     */
    @GetMapping("/enviados")
    public ResponseEntity<?> listarEnviados(
            @RequestParam(defaultValue = "24") int horas) {
        LocalDateTime desde = LocalDateTime.now().minusHours(horas);
        var produtos = repository.findByEnviadoEmAfter(desde);
        return ResponseEntity.ok(Map.of(
            "quantidade", produtos.size(),
            "periodo", "últimas " + horas + " horas",
            "produtos", produtos
        ));
    }
}

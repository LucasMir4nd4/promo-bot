package com.promocoes.bot.config;

import com.promocoes.bot.client.AmazonApiClient;
import com.promocoes.bot.repository.ProdutoEnviadoRepository;
import com.promocoes.bot.service.AliexpressPromoService;
import com.promocoes.bot.service.MercadoLivrePromoService;
import com.promocoes.bot.service.AmazonPromoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BotController {

    private final MercadoLivrePromoService mercadoLivrePromoService;
    private final AmazonPromoService amazonPromoService;
    private final AliexpressPromoService aliexpressService;
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


    @PostMapping("/mercadolivre/executar")
    public ResponseEntity<Map<String, String>> executarManual() {
        log.info("[API] Execução manual solicitada");
        new Thread(mercadoLivrePromoService::processarLinksFixos).start();
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

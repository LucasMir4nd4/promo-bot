package com.promocoes.bot.scheduler;

import com.promocoes.bot.service.AliexpressPromoService;
import com.promocoes.bot.service.MercadoLivrePromoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromoScheduler {

    private final MercadoLivrePromoService mercadoLivreService;
    private final AliexpressPromoService aliexpressService;

    @Scheduled(cron = "${scheduler.cron}")
    public void executarCicloPromocoes() {
        log.info(">>> [SCHEDULER] Disparando ciclo simultâneo: MercadoLivre + AliExpress");

        CompletableFuture<Void> ml = CompletableFuture
                .runAsync(mercadoLivreService::processarPromocoes)
                .exceptionally(e -> {
                    log.error("[SCHEDULER] Erro no ciclo MercadoLivre: {}", e.getMessage());
                    return null;
                });

        CompletableFuture<Void> ali = CompletableFuture
                .runAsync(aliexpressService::processarPromocoes)
                .exceptionally(e -> {
                    log.error("[SCHEDULER] Erro no ciclo AliExpress: {}", e.getMessage());
                    return null;
                });

        CompletableFuture.allOf(ml, ali).join();

        log.info(">>> [SCHEDULER] Ciclo finalizado (todos os sources concluídos)");
    }
}

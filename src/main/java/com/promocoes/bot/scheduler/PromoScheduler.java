package com.promocoes.bot.scheduler;

import com.promocoes.bot.service.MercadoLivrePromoService;
import com.promocoes.bot.service.PromoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler responsável por disparar o ciclo de promoções periodicamente.
 *
 * Configuração do cron em application.yml:
 *   scheduler.cron: "0 0 * * * *"  → executa a cada hora
 *
 * Exemplos de cron:
 *   "0 0 * * * *"    → a cada 1 hora
 *   "0 0/30 * * * *" → a cada 30 minutos
 *   "0 0 9,12,18 * * *" → às 9h, 12h e 18h
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromoScheduler {

    private final MercadoLivrePromoService promoService;

    /**
     * Executa o ciclo de busca e envio de promoções.
     * O cron é lido do application.yml (scheduler.cron).
     */
    @Scheduled(cron = "${scheduler.cron}")
    public void executarCicloPromocoes() {
        log.info(">>> [SCHEDULER] Disparando ciclo de promoções...");
        try {
            promoService.processarLinksFixos();
        } catch (Exception e) {
            log.error("[SCHEDULER] Erro inesperado no ciclo de promoções: {}", e.getMessage(), e);
        }
    }
}

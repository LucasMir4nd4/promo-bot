package com.promocoes.bot.service;

import com.promocoes.bot.client.AliexpressApiClient;
import com.promocoes.bot.client.OpenAiClient;
import com.promocoes.bot.client.TelegramBotClient;
import com.promocoes.bot.client.WhatsAppEvolutionClient;
import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import com.promocoes.bot.model.ProdutoEnviado;
import com.promocoes.bot.repository.ProdutoEnviadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliexpressPromoService {

    private final AliexpressApiClient aliexpressApiClient;
    private final OpenAiClient openAiClient;
    private final TelegramBotClient telegramBotClient;
    private final WhatsAppEvolutionClient whatsAppClient;
    private final ProdutoEnviadoRepository repository;

    @Value("${aliexpress.categorias}")
    private String categoriasConfig;

    @Value("${scheduler.max-produtos}")
    private int maxProdutos;

    public void processarPromocoes() {
        log.info("=== [ALIEXPRESS] Iniciando ciclo por categoria ===");

        List<String> categorias = Arrays.asList(categoriasConfig.split(","));
        int totalEnviados = 0;

        for (String categoria : categorias) {
            if (totalEnviados >= maxProdutos) break;

            int restante = maxProdutos - totalEnviados;
            List<ProdutoDTO> produtos = buscarPorCategoria(categoria.trim(), restante);

            for (ProdutoDTO produto : produtos) {
                if (totalEnviados >= maxProdutos) break;
                if (processarProduto(produto)) totalEnviados++;
            }
        }

        log.info("=== [ALIEXPRESS] Ciclo finalizado. {} enviado(s). ===", totalEnviados);
    }

    @Transactional
    public boolean processarProduto(ProdutoDTO produto) {
        if (repository.existsByAsin(produto.getAsin())) {
            log.debug("[ALIEXPRESS] Produto já enviado, ignorando: {}", produto.getAsin());
            return false;
        }

        log.info("[ALIEXPRESS] Novo produto: {} ({}% OFF)", produto.getTitulo(), produto.getPercentualDesconto());

        CopyPromoDTO copy = openAiClient.gerarCopyPromocional(produto);

        boolean enviadoTelegram = false;
        boolean enviadoWhatsapp = false;

        try {
            enviadoTelegram = telegramBotClient.enviarPromocao(produto, copy);
        } catch (Exception e) {
            log.error("[ALIEXPRESS] Erro ao enviar para Telegram: {}", e.getMessage());
        }

        try {
            enviadoWhatsapp = whatsAppClient.enviarPromocao(produto, copy);
        } catch (Exception e) {
            log.error("[ALIEXPRESS] Erro ao enviar para WhatsApp: {}", e.getMessage());
        }

        if (enviadoTelegram || enviadoWhatsapp) {
            repository.save(ProdutoEnviado.builder()
                    .asin(produto.getAsin())
                    .titulo(produto.getTitulo())
                    .precoAtual(produto.getPrecoAtual())
                    .precoOriginal(produto.getPrecoOriginal())
                    .percentualDesconto(produto.getPercentualDesconto())
                    .urlImagem(produto.getUrlImagem())
                    .urlAfiliado(produto.getUrlAfiliado())
                    .categoria(produto.getCategoria())
                    .enviadoTelegram(enviadoTelegram)
                    .enviadoWhatsapp(enviadoWhatsapp)
                    .build());
            return true;
        }

        log.warn("[ALIEXPRESS] Produto não enviado para nenhum canal: {}", produto.getAsin());
        return false;
    }

    private List<ProdutoDTO> buscarPorCategoria(String categoriaId, int limite) {
        try {
            List<ProdutoDTO> produtos = aliexpressApiClient.buscarPorCategoria(categoriaId, limite);
            log.info("[ALIEXPRESS] {} produto(s) para categoria '{}'", produtos.size(), categoriaId);
            return produtos;
        } catch (Exception e) {
            log.error("[ALIEXPRESS] Erro ao buscar categoria {}: {}", categoriaId, e.getMessage());
            return List.of();
        }
    }
}

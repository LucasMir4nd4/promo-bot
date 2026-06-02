package com.promocoes.bot.service;

import com.promocoes.bot.client.*;
import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import com.promocoes.bot.model.ProdutoEnviado;
import com.promocoes.bot.repository.ProdutoEnviadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serviço principal que orquestra todo o fluxo do bot:
 *
 * 1. Busca produtos em promoção no Mercado Livre (por categoria)
 * 2. Verifica no banco se o produto já foi enviado (evita duplicidade)
 * 3. Gera headline + texto de venda via OpenAI
 * 4. Envia para WhatsApp (Evolution API) e Telegram
 * 5. Persiste o produto como "enviado" no banco
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoLivrePromoService {

    private final MercadoLivreApiClient mercadoLivreApiClient;
    private final OpenAiClient openAiClient;
    private final TelegramBotClient telegramBotClient;
    private final WhatsAppEvolutionClient whatsAppClient;
    private final ProdutoEnviadoRepository repository;

    // IDs de categoria do ML (ex: "MLB1276,MLB1000,MLB1144")
    // Consulte: https://api.mercadolibre.com/sites/MLB/categories
    @Value("${mercadolivre.categorias}")
    private String categoriasConfig;

    @Value("${scheduler.max-produtos}")
    private int maxProdutos;

    /**
     * Ponto de entrada do fluxo principal.
     * Chamado pelo scheduler a cada intervalo configurado.
     */
    public void processarPromocoes() {
        log.info("=== Iniciando ciclo de promoções ===");

        List<String> categorias = Arrays.asList(categoriasConfig.split(","));
        int totalEnviados = 0;

        for (String categoria : categorias) {
            if (totalEnviados >= maxProdutos) {
                log.info("Limite de {} produtos atingido. Encerrando ciclo.", maxProdutos);
                break;
            }

            int restante = maxProdutos - totalEnviados;
            List<ProdutoDTO> produtos = buscarProdutos(categoria.trim(), restante);

            for (ProdutoDTO produto : produtos) {
                if (totalEnviados >= maxProdutos) break;

                boolean enviado = processarProduto(produto);
                if (enviado) totalEnviados++;
            }
        }

        log.info("=== Ciclo finalizado. {} promoções enviadas. ===", totalEnviados);
    }

    /**
     * Processa um único produto: verifica duplicidade, gera copy e envia.
     *
     * @return true se o produto foi enviado com sucesso
     */
    @Transactional
    public boolean processarProduto(ProdutoDTO produto) {
        // ── Passo 1: verificar duplicidade ──────────────────────────────────
        // Usa o ID do item ML (ex: MLB123456) salvo no campo 'asin'
        if (repository.existsByAsin(produto.getAsin())) {
            log.debug("Produto já enviado anteriormente, ignorando: {}", produto.getAsin());
            return false;
        }

        log.info("Novo produto encontrado: {} ({}% OFF)", produto.getTitulo(), produto.getPercentualDesconto());

        // ── Passo 2: gerar copy com IA ────────────────────────────────────
        CopyPromoDTO copy = openAiClient.gerarCopyPromocional(produto);
        log.debug("Copy gerado: {}", copy.getHeadline());

        // ── Passo 3: enviar para as redes sociais ─────────────────────────
        boolean enviadoTelegram = false;
        boolean enviadoWhatsapp = false;

        try {
            enviadoTelegram = telegramBotClient.enviarPromocao(produto, copy);
        } catch (Exception e) {
            log.error("Erro ao enviar para Telegram: {}", e.getMessage());
        }

        try {
            enviadoWhatsapp = whatsAppClient.enviarPromocao(produto, copy);
        } catch (Exception e) {
            log.error("Erro ao enviar para WhatsApp: {}", e.getMessage());
        }

        // ── Passo 4: persistir no banco ───────────────────────────────────
        if (enviadoTelegram || enviadoWhatsapp) {
            ProdutoEnviado entidade = ProdutoEnviado.builder()
                    .asin(produto.getAsin())           // aqui guarda o ID do item ML
                    .titulo(produto.getTitulo())
                    .precoAtual(produto.getPrecoAtual())
                    .precoOriginal(produto.getPrecoOriginal())
                    .percentualDesconto(produto.getPercentualDesconto())
                    .urlImagem(produto.getUrlImagem())
                    .urlAfiliado(produto.getUrlAfiliado())
                    .categoria(produto.getCategoria())
                    .enviadoTelegram(enviadoTelegram)
                    .enviadoWhatsapp(enviadoWhatsapp)
                    .build();

            repository.save(entidade);
            log.info("Produto salvo no banco: {} | Telegram: {} | WhatsApp: {}",
                    produto.getAsin(), enviadoTelegram, enviadoWhatsapp);

            return true;
        }

        log.warn("Produto não foi enviado para nenhuma rede: {}", produto.getAsin());
        return false;
    }

    /**
     * Busca produtos no Mercado Livre para uma categoria.
     * Retorna lista vazia em caso de erro.
     */
    private List<ProdutoDTO> buscarProdutos(String categoriaId, int limite) {
        try {
            List<ProdutoDTO> produtos = mercadoLivreApiClient.buscarPromocoesPorCategoria(categoriaId, limite);
            log.info("ML retornou {} produto(s) para categoria '{}'", produtos.size(), categoriaId);
            return produtos;
        } catch (Exception e) {
            log.error("Erro ao buscar produtos do ML (categoria {}): {}", categoriaId, e.getMessage());
            return new ArrayList<>();
        }
    }
}
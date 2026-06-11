package com.promocoes.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.promocoes.bot.client.*;
import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.LinkDTO;
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
import java.util.Map;

/**
 * Serviço principal que orquestra todo o fluxo do bot.
 *
 * Dois modos de operação:
 *
 * 1. processarLinksFixos() → lê IDs do links.json, busca dados reais no ML,
 *    usa o link de afiliado fixo gerado na mão. Use esse enquanto não tiver
 *    o partner tag do ML configurado.
 *
 * 2. processarPromocoes() → busca produtos automaticamente por categoria
 *    via highlights do ML. Requer access token + partner tag configurados.
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
    private final List<LinkDTO> linksAtivos; // injetado via LinksConfig

    @Value("${mercadolivre.categorias}")
    private String categoriasConfig;

    @Value("${scheduler.max-produtos}")
    private int maxProdutos;

    // =========================================================================
    // MODO 1 — Links fixos do links.json (use esse agora)
    // =========================================================================

    /**
     * Fluxo com links fixos: lê mlbId do links.json, busca dados reais no ML
     * e usa o linkAfiliado fixo gerado na mão.
     *
     * Chamado pelo PromoScheduler enquanto não tiver o partner tag configurado.
     */
    public void processarLinksFixos() {
        log.info("=== Iniciando ciclo com links fixos ({} ativo(s)) ===", linksAtivos.size());
        int totalEnviados = 0;

        for (LinkDTO link : linksAtivos) {
            if (totalEnviados >= maxProdutos) {
                log.info("Limite de {} produtos atingido. Encerrando ciclo.", maxProdutos);
                break;
            }

            try {
                // Busca título, preço, imagem reais direto na API do ML
                ProdutoDTO produto = mercadoLivreApiClient.buscarPorItemId(link.getMlbId());

                // Sobrescreve o link de afiliado com o seu link fixo do JSON
                produto = produto.toBuilder()
                        .urlAfiliado(link.getLinkAfiliado())
                        .build();

                boolean enviado = processarProduto(produto);
                if (enviado) totalEnviados++;

            } catch (Exception e) {
                log.error("Erro ao processar link fixo {}: {}", link.getMlbId(), e.getMessage());
            }
        }

        log.info("=== Ciclo de links fixos finalizado. {} enviado(s). ===", totalEnviados);
    }

    public List<Map<String, String>> buscarCategorias() {

        JsonNode node = mercadoLivreApiClient.buscarCategoria();
        List<Map<String, String>> categorias = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode cat : node) {
                categorias.add(Map.of(
                    "id", cat.path("id").asText(),
                    "nome", cat.path("name").asText()
                ));
            }
        }
        return categorias;
    }

    // =========================================================================
    // MODO 2 — Busca automática por categoria (use quando tiver o partner tag)
    // =========================================================================

    /**
     * Fluxo automático: busca produtos em promoção por categoria no ML.
     * Requer mercadolivre.access-token e mercadolivre.partner-tag no .env.
     */
    public void processarPromocoes() {
        log.info("=== Iniciando ciclo de promoções por categoria ===");

        List<String> categorias = Arrays.asList(categoriasConfig.split(","));
        int totalEnviados = 0;

        for (String categoria : categorias) {
            if (totalEnviados >= maxProdutos) {
                log.info("Limite de {} produtos atingido. Encerrando ciclo.", maxProdutos);
                break;
            }

            int restante = maxProdutos - totalEnviados;
            List<ProdutoDTO> produtos = buscarProdutosPorCategoria(categoria.trim(), restante);

            for (ProdutoDTO produto : produtos) {
                if (totalEnviados >= maxProdutos) break;
                boolean enviado = processarProduto(produto);
                if (enviado) totalEnviados++;
            }
        }

        log.info("=== Ciclo finalizado. {} promoções enviadas. ===", totalEnviados);
    }

    // =========================================================================
    // Fluxo compartilhado pelos dois modos
    // =========================================================================

    /**
     * Processa um único produto: verifica duplicidade, gera copy e envia.
     * Usado pelos dois modos — não muda nada aqui.
     *
     * @return true se o produto foi enviado com sucesso para ao menos um canal
     */
    @Transactional
    public boolean processarProduto(ProdutoDTO produto) {
        // Passo 1: verificar duplicidade pelo ID do item ML (campo 'asin')
        if (repository.existsByAsin(produto.getAsin())) {
            log.debug("Produto já enviado anteriormente, ignorando: {}", produto.getAsin());
            return false;
        }

        log.info("Novo produto: {} ({}% OFF)", produto.getTitulo(), produto.getPercentualDesconto());

        // Passo 2: gerar copy com IA
        CopyPromoDTO copy = openAiClient.gerarCopyPromocional(produto);
        log.debug("Copy gerado: {}", copy.getHeadline());

        // Passo 3: enviar para os canais
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

        // Passo 4: persistir no banco se enviou para ao menos um canal
        if (enviadoTelegram || enviadoWhatsapp) {
            ProdutoEnviado entidade = ProdutoEnviado.builder()
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
                    .build();

            repository.save(entidade);
            log.info("Salvo no banco: {} | Telegram: {} | WhatsApp: {}",
                    produto.getAsin(), enviadoTelegram, enviadoWhatsapp);
            return true;
        }

        log.warn("Produto não foi enviado para nenhum canal: {}", produto.getAsin());
        return false;
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private List<ProdutoDTO> buscarProdutosPorCategoria(String categoriaId, int limite) {
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
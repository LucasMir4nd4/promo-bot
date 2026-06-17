package com.promocoes.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.promocoes.bot.client.*;
import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import com.promocoes.bot.model.LinkFixo;
import com.promocoes.bot.model.ProdutoEnviado;
import com.promocoes.bot.repository.LinkFixoRepository;
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
    private final LinkFixoRepository linkFixoRepository;

    @Value("${mercadolivre.categorias}")
    private String categoriasConfig;

    @Value("${scheduler.max-produtos}")
    private int maxProdutos;

    // =========================================================================
    // MODO 1 — Links fixos do links.json
    // =========================================================================

    /**
     * Fluxo com links fixos: lê mlbId do links.json, busca dados reais no ML
     * e usa o linkAfiliado fixo gerado na mão.
     *
     * Chamado pelo PromoScheduler enquanto não tiver o partner tag configurado.
     */
    public void processarLinksFixos() {
        List<LinkFixo> linksAtivos = linkFixoRepository.findByAtivoTrue();
        log.info("=== Iniciando ciclo com links fixos ({} ativo(s)) ===", linksAtivos.size());
        int totalEnviados = 0;

        for (LinkFixo link : linksAtivos) {
            if (totalEnviados >= maxProdutos) {
                log.info("Limite de {} produtos atingido. Encerrando ciclo.", maxProdutos);
                break;
            }

            if (link.getLinkAfiliado() == null || link.getLinkAfiliado().isBlank()) {
                log.warn("Link fixo {} ativo mas sem link de afiliado, pulando.", link.getMlbId());
                continue;
            }

            try {
                ProdutoDTO produto = montarProdutoDoLink(link);

                if (produto == null) {
                    log.warn("Link fixo {} sem dados (snapshot vazio e item indisponível), pulando.", link.getMlbId());
                    continue;
                }

                boolean enviado = processarProduto(produto);
                if (enviado) totalEnviados++;

            } catch (Exception e) {
                log.error("Erro ao processar link fixo {}: {}", link.getMlbId(), e.getMessage());
            }
        }

        log.info("=== Ciclo de links fixos finalizado. {} enviado(s). ===", totalEnviados);
    }

    /**
     * Monta o ProdutoDTO de um link fixo para publicação, sempre com o linkAfiliado
     * preenchido na mão. Prefere o snapshot capturado pelo bot (evita o GET /items/{id},
     * que pode retornar 403 access_denied); só busca no ML quando não há snapshot
     * (links antigos, anteriores à captura automática).
     */
    private ProdutoDTO montarProdutoDoLink(LinkFixo link) {
        if (link.getTitulo() != null && !link.getTitulo().isBlank()) {
            return ProdutoDTO.builder()
                    .asin(link.getMlbId())
                    .titulo(link.getTitulo())
                    .precoAtual(link.getPrecoAtual())
                    .precoOriginal(link.getPrecoOriginal())
                    .percentualDesconto(link.getPercentualDesconto())
                    .urlImagem(link.getUrlImagem())
                    .urlProduto(link.getUrlProduto())
                    .urlAfiliado(link.getLinkAfiliado())
                    .categoria(link.getCategoria())
                    .fonte("Mercado Livre")
                    .build();
        }

        // Legado: link sem snapshot → busca no ML (sem filtro de desconto).
        ProdutoDTO produto = mercadoLivreApiClient.buscarPorItemId(link.getMlbId(), false);
        if (produto == null) return null;
        return produto.toBuilder().urlAfiliado(link.getLinkAfiliado()).build();
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
     * Fluxo automático: busca produtos em promoção por categoria no ML e os salva
     * como PENDENTES (LinkFixo sem link de afiliado) para revisão manual no front-end.
     * Não posta nada aqui — a publicação acontece via processarLinksFixos depois que
     * o link de afiliado é preenchido na mão.
     */
    public void processarPromocoes() {
        log.info("=== Iniciando ciclo de captura de promoções por categoria ===");

        List<String> categorias = Arrays.asList(categoriasConfig.split(","));
        int totalCapturados = 0;

        for (String categoria : categorias) {
            if (totalCapturados >= maxProdutos) {
                log.info("Limite de {} produtos atingido. Encerrando ciclo.", maxProdutos);
                break;
            }

            int restante = maxProdutos - totalCapturados;
            List<ProdutoDTO> produtos = buscarProdutosPorCategoria(categoria.trim(), restante);

            for (ProdutoDTO produto : produtos) {
                if (totalCapturados >= maxProdutos) break;
                if (capturarComoPendente(produto)) totalCapturados++;
            }
        }

        log.info("=== Ciclo finalizado. {} produto(s) capturado(s) para revisão. ===", totalCapturados);
    }

    /**
     * Salva o produto como LinkFixo PENDENTE (sem link de afiliado, inativo) para
     * revisão manual. Pula duplicados — já capturado/na fila ou já enviado antes.
     *
     * @return true se um novo pendente foi criado
     */
    @Transactional
    public boolean capturarComoPendente(ProdutoDTO produto) {
        String mlbId = produto.getAsin();

        if (linkFixoRepository.existsByMlbId(mlbId)) {
            log.debug("Produto já na fila de revisão, ignorando: {}", mlbId);
            return false;
        }
        if (repository.existsByAsin(mlbId)) {
            log.debug("Produto já enviado anteriormente, ignorando: {}", mlbId);
            return false;
        }

        LinkFixo pendente = LinkFixo.builder()
                .mlbId(mlbId)
                .linkAfiliado(null)   // preenchido na mão pelo front-end
                .ativo(false)         // pendente até a revisão
                .titulo(produto.getTitulo())
                .precoAtual(produto.getPrecoAtual())
                .precoOriginal(produto.getPrecoOriginal())
                .percentualDesconto(produto.getPercentualDesconto())
                .urlImagem(produto.getUrlImagem())
                .urlProduto(produto.getUrlProduto())
                .categoria(produto.getCategoria())
                .build();

        linkFixoRepository.save(pendente);
        log.info("Capturado para revisão: {} ({}% OFF) | {}", produto.getTitulo(),
                produto.getPercentualDesconto(), mlbId);
        return true;
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
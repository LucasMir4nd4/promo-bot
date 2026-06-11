package com.promocoes.bot.mock;

import com.promocoes.bot.dto.ProdutoDTO;
import com.promocoes.bot.service.MercadoLivreAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Primary
@Profile("mock")
public class MockMercadoLivreApiClient extends com.promocoes.bot.client.MercadoLivreApiClient {

    private static final AtomicInteger contador = new AtomicInteger(0);

    private static final List<ProdutoDTO> PRODUTOS_FALSOS = List.of(
        ProdutoDTO.builder()
            .asin("MLB3939769540")
            .titulo("Smartphone Samsung Galaxy A54 5G 128GB 8GB RAM")
            .precoAtual(new BigDecimal("1499.00"))
            .precoOriginal(new BigDecimal("2299.00"))
            .percentualDesconto(35)
            .urlImagem("https://http2.mlstatic.com/D_NQ_NP_sample1.jpg")
            .urlProduto("https://www.mercadolivre.com.br/p/MLB3939769540")
            .urlAfiliado("https://www.mercadolivre.com.br/p/MLB3939769540?matt_tool=mock")
            .categoria("MLB1000")
            .fonte("Mercado Livre")
            .build(),

        ProdutoDTO.builder()
            .asin("MLB2847563910")
            .titulo("Notebook Lenovo IdeaPad 3 Intel Core i5 8GB 256GB SSD")
            .precoAtual(new BigDecimal("2799.00"))
            .precoOriginal(new BigDecimal("4199.00"))
            .percentualDesconto(33)
            .urlImagem("https://http2.mlstatic.com/D_NQ_NP_sample2.jpg")
            .urlProduto("https://www.mercadolivre.com.br/p/MLB2847563910")
            .urlAfiliado("https://www.mercadolivre.com.br/p/MLB2847563910?matt_tool=mock")
            .categoria("MLB1144")
            .fonte("Mercado Livre")
            .build(),

        ProdutoDTO.builder()
            .asin("MLB1736492058")
            .titulo("Smart TV Samsung 50\" 4K QLED")
            .precoAtual(new BigDecimal("2199.00"))
            .precoOriginal(new BigDecimal("3499.00"))
            .percentualDesconto(37)
            .urlImagem("https://http2.mlstatic.com/D_NQ_NP_sample3.jpg")
            .urlProduto("https://www.mercadolivre.com.br/p/MLB1736492058")
            .urlAfiliado("https://www.mercadolivre.com.br/p/MLB1736492058?matt_tool=mock")
            .categoria("MLB1246")
            .fonte("Mercado Livre")
            .build(),

        ProdutoDTO.builder()
            .asin("MLB9284756103")
            .titulo("Fone de Ouvido JBL Tune 510BT Bluetooth")
            .precoAtual(new BigDecimal("199.00"))
            .precoOriginal(new BigDecimal("349.00"))
            .percentualDesconto(43)
            .urlImagem("https://http2.mlstatic.com/D_NQ_NP_sample4.jpg")
            .urlProduto("https://www.mercadolivre.com.br/p/MLB9284756103")
            .urlAfiliado("https://www.mercadolivre.com.br/p/MLB9284756103?matt_tool=mock")
            .categoria("MLB1276")
            .fonte("Mercado Livre")
            .build(),

        ProdutoDTO.builder()
            .asin("MLB5647382910")
            .titulo("Câmera de Segurança Intelbras iM5 Full HD WiFi")
            .precoAtual(new BigDecimal("299.00"))
            .precoOriginal(new BigDecimal("499.00"))
            .percentualDesconto(40)
            .urlImagem("https://http2.mlstatic.com/D_NQ_NP_sample5.jpg")
            .urlProduto("https://www.mercadolivre.com.br/p/MLB5647382910")
            .urlAfiliado("https://www.mercadolivre.com.br/p/MLB5647382910?matt_tool=mock")
            .categoria("MLB1276")
            .fonte("Mercado Livre")
            .build()
    );

    public MockMercadoLivreApiClient(MercadoLivreAuthService authService) {
        super(authService);
    }

    @Override
    public List<ProdutoDTO> buscarPromocoesPorCategoria(String categoriaId, int maxResultados) {
        log.info("[MOCK] Simulando busca ML para categoria '{}' (sem API real)", categoriaId);

        int idx = contador.getAndIncrement() % PRODUTOS_FALSOS.size();
        ProdutoDTO produto = PRODUTOS_FALSOS.get(idx);

        String asinUnico = produto.getAsin() + "_" + System.currentTimeMillis() % 10000;
        produto = produto.toBuilder()
                .asin(asinUnico)
                .build();

        log.info("[MOCK] Produto simulado: {} ({}% OFF)", produto.getTitulo(), produto.getPercentualDesconto());
        return List.of(produto);
    }

    @Override
    public ProdutoDTO buscarPorItemId(String itemId) {
        log.info("[MOCK] Simulando busca ML por itemId '{}'", itemId);

        int idx = contador.getAndIncrement() % PRODUTOS_FALSOS.size();
        return PRODUTOS_FALSOS.get(idx).toBuilder()
                .asin(itemId)
                .build();
    }
}
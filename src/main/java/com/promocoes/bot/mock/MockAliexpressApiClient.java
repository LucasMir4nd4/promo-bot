package com.promocoes.bot.mock;

import com.promocoes.bot.dto.ProdutoDTO;
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
public class MockAliexpressApiClient extends com.promocoes.bot.client.AliexpressApiClient {

    private static final AtomicInteger contador = new AtomicInteger(0);

    private static final List<ProdutoDTO> PRODUTOS_FALSOS = List.of(
        ProdutoDTO.builder()
            .asin("1005005885373836")
            .titulo("Fone Bluetooth TWS AliExpress Pro Max Noise Cancelling")
            .precoAtual(new BigDecimal("89.90"))
            .precoOriginal(new BigDecimal("199.90"))
            .percentualDesconto(55)
            .urlImagem("https://ae01.alicdn.com/kf/sample1.jpg")
            .urlProduto("https://aliexpress.com/item/1005005885373836.html")
            .urlAfiliado("https://aliexpress.com/item/1005005885373836.html?aff_fcid=mock")
            .categoria("509")
            .fonte("AliExpress")
            .build(),

        ProdutoDTO.builder()
            .asin("1005004867123456")
            .titulo("Smartwatch HW67 Ultra Series 8 NFC Tela 2.0\"")
            .precoAtual(new BigDecimal("149.90"))
            .precoOriginal(new BigDecimal("350.00"))
            .percentualDesconto(57)
            .urlImagem("https://ae01.alicdn.com/kf/sample2.jpg")
            .urlProduto("https://aliexpress.com/item/1005004867123456.html")
            .urlAfiliado("https://aliexpress.com/item/1005004867123456.html?aff_fcid=mock")
            .categoria("509")
            .fonte("AliExpress")
            .build(),

        ProdutoDTO.builder()
            .asin("1005003456789012")
            .titulo("Câmera de Segurança WiFi 4MP Full HD Visão Noturna")
            .precoAtual(new BigDecimal("119.90"))
            .precoOriginal(new BigDecimal("280.00"))
            .percentualDesconto(57)
            .urlImagem("https://ae01.alicdn.com/kf/sample3.jpg")
            .urlProduto("https://aliexpress.com/item/1005003456789012.html")
            .urlAfiliado("https://aliexpress.com/item/1005003456789012.html?aff_fcid=mock")
            .categoria("200000606")
            .fonte("AliExpress")
            .build(),

        ProdutoDTO.builder()
            .asin("1005002345678901")
            .titulo("Carregador Turbo 65W GaN USB-C 3 Portas Universal")
            .precoAtual(new BigDecimal("59.90"))
            .precoOriginal(new BigDecimal("139.90"))
            .percentualDesconto(57)
            .urlImagem("https://ae01.alicdn.com/kf/sample4.jpg")
            .urlProduto("https://aliexpress.com/item/1005002345678901.html")
            .urlAfiliado("https://aliexpress.com/item/1005002345678901.html?aff_fcid=mock")
            .categoria("509")
            .fonte("AliExpress")
            .build(),

        ProdutoDTO.builder()
            .asin("1005001234567890")
            .titulo("Luminária LED RGB Gaming Desk Lamp Controle Touch")
            .precoAtual(new BigDecimal("49.90"))
            .precoOriginal(new BigDecimal("120.00"))
            .percentualDesconto(58)
            .urlImagem("https://ae01.alicdn.com/kf/sample5.jpg")
            .urlProduto("https://aliexpress.com/item/1005001234567890.html")
            .urlAfiliado("https://aliexpress.com/item/1005001234567890.html?aff_fcid=mock")
            .categoria("200003498")
            .fonte("AliExpress")
            .build()
    );

    @Override
    public List<ProdutoDTO> buscarProdutosEmPromocao(int maxResultados) {
        log.info("[MOCK] AliExpress - simulando busca sem API real");
        return getProdutoUnico();
    }

    @Override
    public List<ProdutoDTO> buscarPorCategoria(String categoriaId, int maxResultados) {
        log.info("[MOCK] AliExpress - simulando busca por categoria '{}' sem API real", categoriaId);
        return getProdutoUnico();
    }

    @Override
    public ProdutoDTO buscarPorProductId(String productId) {
        log.info("[MOCK] AliExpress - simulando busca por productId '{}'", productId);
        int idx = contador.getAndIncrement() % PRODUTOS_FALSOS.size();
        return PRODUTOS_FALSOS.get(idx).toBuilder()
                .asin(productId)
                .build();
    }

    private List<ProdutoDTO> getProdutoUnico() {
        int idx = contador.getAndIncrement() % PRODUTOS_FALSOS.size();
        ProdutoDTO produto = PRODUTOS_FALSOS.get(idx);
        String asinUnico = produto.getAsin() + "_" + System.currentTimeMillis() % 10000;
        return List.of(produto.toBuilder().asin(asinUnico).build());
    }
}
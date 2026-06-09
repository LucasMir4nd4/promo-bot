package com.promocoes.bot.mock;

import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Substituto MOCK da AmazonApiClient.
 * Ativo apenas com o perfil "mock" (-Dspring.profiles.active=mock).
 *
 * Retorna produtos falsos para testar o fluxo completo sem API real.
 * @Primary faz o Spring preferir este bean no lugar do original.
 */
@Slf4j
@Component
@Primary
@Profile("mock")
public class MockAmazonApiClient extends com.promocoes.bot.client.AmazonApiClient {

    private static final AtomicInteger contador = new AtomicInteger(0);
    private final Random random = new Random();

    // Catálogo de produtos falsos para simular ofertas variadas
    private static final List<ProdutoDTO> PRODUTOS_FALSOS = List.of(
        ProdutoDTO.builder()
            .asin("B08N5WRWNW")
            .titulo("Echo Dot (5ª Geração) | Smart speaker com Alexa")
            .precoAtual(new BigDecimal("179.00"))
            .precoOriginal(new BigDecimal("349.00"))
            .percentualDesconto(49)
            .urlImagem("https://m.media-amazon.com/images/I/514L-q7PFML._AC_SL1000_.jpg")
            .urlProduto("https://www.amazon.com.br/dp/B08N5WRWNW")
            .urlAfiliado("https://www.amazon.com.br/dp/B08N5WRWNW?tag=mock-tag-20")
            .categoria("Eletronicos")
            .build(),

        ProdutoDTO.builder()
            .asin("B09G9FPHY6")
            .titulo("Kindle (11ª Geração) 16 GB – Com iluminação embutida")
            .precoAtual(new BigDecimal("299.00"))
            .precoOriginal(new BigDecimal("499.00"))
            .percentualDesconto(40)
            .urlImagem("https://m.media-amazon.com/images/I/61V9PpctziL._AC_SL1000_.jpg")
            .urlProduto("https://www.amazon.com.br/dp/B09G9FPHY6")
            .urlAfiliado("https://www.amazon.com.br/dp/B09G9FPHY6?tag=mock-tag-20")
            .categoria("Eletronicos")
            .build(),

        ProdutoDTO.builder()
            .asin("B07PXGQC1Q")
            .titulo("Samsung Galaxy Buds2 Pro, Bluetooth, Cancelamento de Ruído")
            .precoAtual(new BigDecimal("499.00"))
            .precoOriginal(new BigDecimal("899.00"))
            .percentualDesconto(44)
            .urlImagem("https://m.media-amazon.com/images/I/61iqFHSF-qL._AC_SL1500_.jpg")
            .urlProduto("https://www.amazon.com.br/dp/B07PXGQC1Q")
            .urlAfiliado("https://www.amazon.com.br/dp/B07PXGQC1Q?tag=mock-tag-20")
            .categoria("Eletronicos")
            .build(),

        ProdutoDTO.builder()
            .asin("B0BDHX8Z63")
            .titulo("Notebook Samsung Book2 Go Intel Core i3 4GB 256GB SSD 15.6")
            .precoAtual(new BigDecimal("1799.00"))
            .precoOriginal(new BigDecimal("2999.00"))
            .percentualDesconto(40)
            .urlImagem("https://m.media-amazon.com/images/I/71nfJSh3zKL._AC_SL1500_.jpg")
            .urlProduto("https://www.amazon.com.br/dp/B0BDHX8Z63")
            .urlAfiliado("https://www.amazon.com.br/dp/B0BDHX8Z63?tag=mock-tag-20")
            .categoria("Informatica")
            .build(),

        ProdutoDTO.builder()
            .asin("B09JQMJHXY")
            .titulo("Fire TV Stick 4K Max | Streaming em 4K com Wi-Fi 6")
            .precoAtual(new BigDecimal("249.00"))
            .precoOriginal(new BigDecimal("449.00"))
            .percentualDesconto(45)
            .urlImagem("https://m.media-amazon.com/images/I/51TjJOTfslL._AC_SL1000_.jpg")
            .urlProduto("https://www.amazon.com.br/dp/B09JQMJHXY")
            .urlAfiliado("https://www.amazon.com.br/dp/B09JQMJHXY?tag=mock-tag-20")
            .categoria("Eletronicos")
            .build()
    );

    @Override
    public List<ProdutoDTO> buscarPromocoesPorCategoria(String categoria, int maxResultados) {
        log.info("[MOCK] Simulando busca Amazon para categoria '{}' (sem API real)", categoria);

        // Retorna um produto diferente a cada chamada para simular variedade
        int idx = contador.getAndIncrement() % PRODUTOS_FALSOS.size();
        ProdutoDTO produto = PRODUTOS_FALSOS.get(idx);

        // Varia o ASIN ligeiramente para não repetir no banco durante testes
        String asinUnico = produto.getAsin() + "_" + System.currentTimeMillis() % 10000;
        produto = ProdutoDTO.builder()
            .asin(asinUnico)
            .titulo(produto.getTitulo())
            .precoAtual(produto.getPrecoAtual())
            .precoOriginal(produto.getPrecoOriginal())
            .percentualDesconto(produto.getPercentualDesconto())
            .urlImagem(produto.getUrlImagem())
            .urlProduto(produto.getUrlProduto())
            .urlAfiliado(produto.getUrlAfiliado())
            .categoria(produto.getCategoria())
            .fonte("Amazon")
            .build();

        log.info("[MOCK] Produto simulado: {} ({}% OFF)", produto.getTitulo(), produto.getPercentualDesconto());
        return List.of(produto);
    }
}

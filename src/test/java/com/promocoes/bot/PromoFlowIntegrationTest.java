package com.promocoes.bot;

import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import com.promocoes.bot.repository.ProdutoEnviadoRepository;
import com.promocoes.bot.service.AmazonPromoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração usando o perfil mock.
 * Roda com banco H2 em memória e todos os clients simulados.
 * Não precisa de nenhuma API real para executar.
 *
 * Execute com: mvn test
 */
@SpringBootTest
@ActiveProfiles("mock")
class PromoFlowIntegrationTest {

    @Autowired
    private AmazonPromoService promoService;

    @Autowired
    private ProdutoEnviadoRepository repository;

    @Test
    @DisplayName("Deve processar produto novo e salvar no banco")
    void deveProcessarProdutoNovoESalvar() {
        // Arrange
        ProdutoDTO produto = produtoFalso("ASIN_TESTE_001");

        // Act
        boolean resultado = promoService.processarProduto(produto);

        // Assert
        assertThat(resultado).isTrue();
        assertThat(repository.existsByAsin("ASIN_TESTE_001")).isTrue();
    }

    @Test
    @DisplayName("Não deve reenviar produto que já está no banco")
    void naoDeveReenviarProdutoDuplicado() {
        // Processa pela primeira vez
        ProdutoDTO produto = produtoFalso("ASIN_DUPLICADO_001");
        promoService.processarProduto(produto);

        // Act – tenta processar de novo o mesmo produto
        boolean resultado = promoService.processarProduto(produto);

        // Assert – deve ignorar
        assertThat(resultado).isFalse();
        // No banco deve ter apenas 1 registro (não duplicou)
        assertThat(repository.findByAsin("ASIN_DUPLICADO_001")).isPresent();
    }

    @Test
    @DisplayName("Deve executar ciclo completo sem erro")
    void deveExecutarCicloCompleto() {
        // Roda o fluxo inteiro igual ao scheduler faria
        promoService.processarPromocoes();

        // Verifica que algum produto foi salvo
        assertThat(repository.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Banco deve registrar envio para Telegram e WhatsApp")
    void deveRegistrarCanalDeEnvio() {
        ProdutoDTO produto = produtoFalso("ASIN_CANAL_001");
        promoService.processarProduto(produto);

        var salvo = repository.findByAsin("ASIN_CANAL_001");
        assertThat(salvo).isPresent();
        assertThat(salvo.get().getEnviadoTelegram()).isTrue();
        assertThat(salvo.get().getEnviadoWhatsapp()).isTrue();
    }

    // ── helpers ────────────────────────────────────────────────

    private ProdutoDTO produtoFalso(String asin) {
        return ProdutoDTO.builder()
            .asin(asin)
            .titulo("Produto de Teste " + asin)
            .precoAtual(new BigDecimal("199.00"))
            .precoOriginal(new BigDecimal("399.00"))
            .percentualDesconto(50)
            .urlImagem("https://via.placeholder.com/300")
            .urlProduto("https://www.amazon.com.br/dp/" + asin)
            .urlAfiliado("https://www.amazon.com.br/dp/" + asin + "?tag=mock-tag-20")
            .categoria("Eletronicos")
            .build();
    }
}

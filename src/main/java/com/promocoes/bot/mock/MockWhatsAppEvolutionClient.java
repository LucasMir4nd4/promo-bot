package com.promocoes.bot.mock;

import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Substituto MOCK do WhatsAppEvolutionClient.
 * Imprime a mensagem no console em vez de enviar para grupos do WhatsApp.
 */
@Slf4j
@Component
@Primary
@Profile("mock")
public class MockWhatsAppEvolutionClient extends com.promocoes.bot.client.WhatsAppEvolutionClient {

    @Override
    public boolean enviarPromocao(ProdutoDTO produto, CopyPromoDTO copy) {
        String mensagem = formatarParaConsole(produto, copy);

        log.info("\n");
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║        [MOCK WHATSAPP] Mensagem que seria enviada    ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        for (String linha : mensagem.split("\n")) {
            log.info("║  {}", linha);
        }
        log.info("╚══════════════════════════════════════════════════════╝");

        return true; // Simula envio bem-sucedido
    }

    private String formatarParaConsole(ProdutoDTO produto, CopyPromoDTO copy) {
        return String.format(
            "*%s*\n\n" +
            "%s\n\n" +
            "💰 ~R$ %.2f~ → *R$ %.2f*\n" +
            "📉 *%d%% OFF* | Economia de *R$ %.2f*\n\n" +
            "🖼️  Imagem: %s\n" +
            "👇 Link: %s",
            copy.getHeadline(),
            copy.getTextoVenda(),
            produto.getPrecoOriginal(),
            produto.getPrecoAtual(),
            produto.getPercentualDesconto(),
            produto.getValorEconomizado(),
            produto.getUrlImagem(),
            produto.getUrlAfiliado()
        );
    }
}

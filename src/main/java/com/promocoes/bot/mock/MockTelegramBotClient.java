package com.promocoes.bot.mock;

import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Substituto MOCK do TelegramBotClient.
 * Em vez de enviar para o Telegram, imprime a mensagem formatada no console.
 * Assim você vê exatamente o que seria enviado.
 */
@Slf4j
@Component
@Primary
@Profile("mock")
public class MockTelegramBotClient extends com.promocoes.bot.client.TelegramBotClient {

    public MockTelegramBotClient(
            @Value("${telegram.bot-token}") String token
    ) {
        super(token);
    }

    @Value("${telegram.real:false}")
    private boolean useRealTelegram;

    @Value("${telegram.chat-id}")
    private String chatIdReal;

    @Override
    public boolean enviarPromocao(ProdutoDTO produto, CopyPromoDTO copy) {
        String mensagem = formatarParaConsole(produto, copy);

        if (useRealTelegram) {
            log.warn("⚠️  Modo REAL Telegram ativado! Esta mensagem será enviada para o Telegram.");
            return super.enviarPromocao(produto, copy);
        }

        log.info("\n");
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║         [MOCK TELEGRAM] Mensagem que seria enviada   ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        for (String linha : mensagem.split("\n")) {
            log.info("║  {}", linha);
        }
        log.info("╚══════════════════════════════════════════════════════╝");

        return true; // Simula envio bem-sucedido
    }

    private String formatarParaConsole(ProdutoDTO produto, CopyPromoDTO copy) {
        return String.format(
            "%s\n\n" +
            "%s\n\n" +
            "💰 De: R$ %.2f  →  Por: R$ %.2f\n" +
            "📉 %d%% OFF  |  Economia: R$ %.2f\n\n" +
            "🖼️  Imagem: %s\n" +
            "🔗 Link afiliado: %s",
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

    // Obrigatório pela interface – não faz nada no mock
    @Override
    public void onUpdateReceived(Update update) {}

    @Override
    public String getBotUsername() {
        return "MockTelegramBot";
    }
}

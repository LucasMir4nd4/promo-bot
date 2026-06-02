package com.promocoes.bot.client;

import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Client do Telegram Bot.
 * Envia mensagens com imagem e texto para o canal/grupo configurado.
 *
 * Como configurar:
 * 1. Fale com @BotFather no Telegram → /newbot → copie o token
 * 2. Crie um canal/grupo e adicione seu bot como administrador
 * 3. Preencha TELEGRAM_BOT_TOKEN e TELEGRAM_CHAT_ID no .env
 */
@Slf4j
@Component
public class TelegramBotClient extends TelegramLongPollingBot {

    @Value("${telegram.bot-username}")
    private String botUsername;

    @Value("${telegram.chat-id}")
    private String chatId;

    public TelegramBotClient(@Value("${telegram.bot-token}") String botToken) {
        super(botToken);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Envia uma promoção formatada para o canal do Telegram.
     * Inclui imagem do produto + headline + texto de venda + link de afiliado.
     */
    public boolean enviarPromocao(ProdutoDTO produto, CopyPromoDTO copy) {
        try {
            String mensagem = formatarMensagem(produto, copy);

            if (produto.getUrlImagem() != null && !produto.getUrlImagem().isBlank()) {
                // Envia com imagem
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId);
                sendPhoto.setPhoto(new InputFile(produto.getUrlImagem()));
                sendPhoto.setCaption(mensagem);
                sendPhoto.setParseMode("HTML");
                execute(sendPhoto);
            } else {
                // Envia só texto
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setText(mensagem);
                sendMessage.setParseMode("HTML");
                execute(sendMessage);
            }

            log.info("[TELEGRAM] Promoção enviada: {}", produto.getTitulo());
            return true;

        } catch (TelegramApiException e) {
            log.error("[TELEGRAM] Erro ao enviar promoção: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Formata a mensagem com HTML (suportado pelo Telegram).
     */
    private String formatarMensagem(ProdutoDTO produto, CopyPromoDTO copy) {
        return String.format(
            "<b>%s</b>\n\n" +
            "%s\n\n" +
            "💰 <s>R$ %.2f</s>  →  <b>R$ %.2f</b>\n" +
            "📉 <b>%d%% OFF</b>  |  Economia de R$ %.2f\n\n" +
            "🛒 <a href=\"%s\">COMPRAR AGORA NA AMAZON</a>",
            copy.getHeadline(),
            copy.getTextoVenda(),
            produto.getPrecoOriginal(),
            produto.getPrecoAtual(),
            produto.getPercentualDesconto(),
            produto.getValorEconomizado(),
            produto.getUrlAfiliado()
        );
    }

    /**
     * Recebe updates (mensagens enviadas ao bot).
     * Não é necessário para o fluxo de envio, mas obrigatório pela interface.
     */
    @Override
    public void onUpdateReceived(Update update) {
        // Bot só envia, não precisa processar respostas
        log.debug("[TELEGRAM] Update recebido (ignorado): {}", update.getUpdateId());
    }
}

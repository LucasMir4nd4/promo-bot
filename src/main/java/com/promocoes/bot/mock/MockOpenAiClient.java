package com.promocoes.bot.mock;

import com.promocoes.bot.dto.CopyPromoDTO;
import com.promocoes.bot.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * Substituto MOCK do OpenAiClient.
 * Retorna textos de venda pré-prontos sem chamar a API da OpenAI.
 */
@Slf4j
@Component
@Primary
@Profile("mock")
public class MockOpenAiClient extends com.promocoes.bot.client.OpenAiClient {

    private final Random random = new Random();

    private static final List<String> HEADLINES = List.of(
        "🔥 OFERTA RELÂMPAGO! Preço nunca visto antes!",
        "🚨 ALERTA DE PROMOÇÃO! Corre que é por tempo limitado!",
        "💥 DESCONTO ABSURDO! Não vai encontrar mais barato!",
        "⚡ QUEIMA DE ESTOQUE! Últimas unidades com esse preço!",
        "🎯 OPORTUNIDADE ÚNICA! Garante o seu antes que acabe!"
    );

    private static final List<String> TEXTOS = List.of(
        "Essa é uma daquelas promoções que você vai se arrepender de não aproveitar. Preço histórico, disponibilidade limitada — não perde tempo!",
        "Produto top com avaliação 4.8 estrelas, agora com desconto imperdível. O preço pode voltar a qualquer momento, então aproveita agora!",
        "Amazon liberou essa oferta por tempo limitado. Já foi vendido mais de 10.000 vezes e as avaliações são incríveis. Garanta o seu!",
        "Quem esperou, ganhou! Esse produto finalmente entrou em promoção. Estoque reduzido — primeiro a pagar, primeiro a levar.",
        "Uma das melhores avaliações da categoria com um preço que raramente aparece. Eu mesmo estou de olho nesse. Aproveita!"
    );

    private static final List<String> EMOJIS = List.of("🛒", "🎁", "💻", "📱", "🎧", "📺", "⌨️");

    @Override
    public CopyPromoDTO gerarCopyPromocional(ProdutoDTO produto) {
        log.info("[MOCK] Gerando copy simulado para: {} (sem OpenAI)", produto.getTitulo());

        String headline = HEADLINES.get(random.nextInt(HEADLINES.size()));
        String texto    = TEXTOS.get(random.nextInt(TEXTOS.size()));
        String emoji    = EMOJIS.get(random.nextInt(EMOJIS.size()));

        CopyPromoDTO copy = CopyPromoDTO.builder()
            .headline(headline)
            .textoVenda(produto.getTitulo() + "\n\n" +texto)
            .emoji(emoji)
            .build();

        log.info("[MOCK] Copy gerado: {}", copy.getHeadline());
        return copy;
    }
}

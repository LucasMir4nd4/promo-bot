package com.promocoes.bot.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO com o texto gerado pela OpenAI para a promoção.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyPromoDTO {

    /** Headline chamativa (ex: "🔥 MEGA OFERTA! Notebook Samsung pela metade do preço!") */
    private String headline;

    /** Texto persuasivo de venda */
    private String textoVenda;

    /** Emoji temático para o produto */
    private String emoji;
}

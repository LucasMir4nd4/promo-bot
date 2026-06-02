package com.promocoes.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa uma entrada do links.json.
 * Contém só o ID do produto no ML e o link de afiliado gerado na mão.
 */
public class LinkDTO {

    @JsonProperty("mlbId")
    private String mlbId;          // ex: "MLB3939769540"

    @JsonProperty("linkAfiliado")
    private String linkAfiliado;   // link fixo gerado pelo programa de afiliados

    @JsonProperty("ativo")
    private boolean ativo;

    public String getMlbId()        { return mlbId; }
    public String getLinkAfiliado() { return linkAfiliado; }
    public boolean isAtivo()        { return ativo; }
}
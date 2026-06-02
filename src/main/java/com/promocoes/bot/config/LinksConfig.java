package com.promocoes.bot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promocoes.bot.dto.LinkDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class LinksConfig {

    private static final Logger log = LoggerFactory.getLogger(LinksConfig.class);

    /**
     * Lê o links.json de resources/ e expõe apenas os links com "ativo": true.
     */
    @Bean
    public List<LinkDTO> linksAtivos() {
        List<LinkDTO> todos = carregarLinks();
        List<LinkDTO> ativos = todos.stream()
                .filter(LinkDTO::isAtivo)
                .collect(Collectors.toList());

        log.info("📋 links.json: {} total, {} ativo(s)", todos.size(), ativos.size());
        return ativos;
    }

    private List<LinkDTO> carregarLinks() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("links.json").getInputStream();
            JsonNode root = mapper.readTree(is);
            JsonNode linksNode = root.get("links");

            List<LinkDTO> lista = new ArrayList<>();
            if (linksNode != null && linksNode.isArray()) {
                for (JsonNode node : linksNode) {
                    lista.add(mapper.treeToValue(node, LinkDTO.class));
                }
            }
            return lista;
        } catch (Exception e) {
            log.error("❌ Erro ao carregar links.json: {}", e.getMessage());
            return List.of();
        }
    }
}
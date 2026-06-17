package com.promocoes.bot.controller;

import com.promocoes.bot.model.LinkFixo;
import com.promocoes.bot.repository.LinkFixoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkFixoController {

    private final LinkFixoRepository repository;

    @GetMapping
    public List<LinkFixo> listar() {
        return repository.findAll();
    }

    /** Itens capturados pelo bot aguardando o link de afiliado ser preenchido na mão. */
    @GetMapping("/pendentes")
    public List<LinkFixo> pendentes() {
        return repository.findByLinkAfiliadoIsNullOrderByIdDesc();
    }

    /**
     * Preenche o link de afiliado de um pendente e já o ativa (pronto para postar).
     * Body: { "linkAfiliado": "https://..." }
     */
    @PatchMapping("/{id}/afiliado")
    public ResponseEntity<?> definirAfiliado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String linkAfiliado = body.get("linkAfiliado");
        if (linkAfiliado == null || linkAfiliado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "linkAfiliado é obrigatório."));
        }
        return repository.findById(id).map(link -> {
            link.setLinkAfiliado(linkAfiliado.trim());
            link.setAtivo(true);
            return ResponseEntity.ok((Object) repository.save(link));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<?> ativar(@PathVariable Long id) {
        return repository.findById(id).map(link -> {
            if (link.getLinkAfiliado() == null || link.getLinkAfiliado().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("erro", "Preencha o link de afiliado antes de ativar."));
            }
            link.setAtivo(true);
            return ResponseEntity.ok((Object) repository.save(link));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<LinkFixo> desativar(@PathVariable Long id) {
        return repository.findById(id).map(link -> {
            link.setAtivo(false);
            return ResponseEntity.ok(repository.save(link));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletar(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("mensagem", "Link removido com sucesso."));
    }
}

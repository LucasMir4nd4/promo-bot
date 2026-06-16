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

    @PostMapping
    public ResponseEntity<LinkFixo> adicionar(@RequestBody LinkFixo link) {
        link.setId(null);
        return ResponseEntity.ok(repository.save(link));
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<LinkFixo> ativar(@PathVariable Long id) {
        return repository.findById(id).map(link -> {
            link.setAtivo(true);
            return ResponseEntity.ok(repository.save(link));
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

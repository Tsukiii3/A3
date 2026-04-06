package com.phishguard.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.phishguard.demo.service.*;
import com.phishguard.demo.model.*;
import com.phishguard.demo.dto.*;

@RestController
@RequestMapping("/api/url")
public class UrlController {

    @Autowired
    private UrlService service;

    @PostMapping("/analyze")
    public UrlAnalysis analyze(@RequestBody UrlRequest request) {
        return service.analyze(request);
    }
    @GetMapping
    public List<UrlAnalysis> listar() {
        return service.listar();
    }
    @GetMapping("/{id}")
    public ResponseEntity<UrlAnalysis> buscar(@PathVariable Long id) {
        UrlAnalysis url = service.buscarPorId(id);

        if (url == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(url);
    }
    @PutMapping("/{id}")
    public UrlAnalysis atualizar(@PathVariable Long id, @RequestBody UrlRequest request) {
        return service.atualizar(id, request);
    }
    @DeleteMapping("/{id}")
    public String deletar(@PathVariable Long id) {
        service.deletar(id);
        return "Deletado com sucesso!";
    }
}
package com.phishguard.demo.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.phishguard.demo.model.UrlAnalysis;
import com.phishguard.demo.repository.UrlRepository;
import com.phishguard.demo.dto.UrlRequest;;

@Service
public class UrlService {
     @Autowired
    private UrlRepository repository;

  public UrlAnalysis analyze(UrlRequest request) {
    UrlAnalysis result = new UrlAnalysis();

    result.setUrl(request.getUrl());
    result.setScore(request.getScore());
    result.setStatus(request.getStatus());

    return repository.save(result);
}
    public List<UrlAnalysis> listar() {
        return repository.findAll();
    }
    public UrlAnalysis buscarPorId(Long id) {
        return repository.findById(id).orElse(null);
    }
    public UrlAnalysis atualizar(Long id, UrlRequest request) {
    UrlAnalysis existente = repository.findById(id).orElse(null);

    if (existente != null) {
        existente.setUrl(request.getUrl());
        existente.setScore(request.getScore());
        existente.setStatus(request.getStatus());

        return repository.save(existente);
    }
    return null;
}
    public void deletar(Long id) {
        repository.deleteById(id);
    }
}

package com.agroflow.central.web;

import com.agroflow.central.model.Agricultor;
import com.agroflow.central.repo.AgricultorRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agricultores")
public class AgricultorController {

    private final AgricultorRepo repo;

    public AgricultorController(AgricultorRepo repo) {
        this.repo = repo;
    }

    @PostMapping
    public Agricultor create(@RequestBody Agricultor a) { return repo.save(a); }

    @GetMapping
    public List<Agricultor> list() { return repo.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Agricultor> get(@PathVariable UUID id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Agricultor> update(@PathVariable UUID id, @RequestBody Agricultor a) {
        return repo.findById(id).map(existing -> {
            existing.setNombre(a.getNombre());
            existing.setFinca(a.getFinca());
            existing.setUbicacion(a.getUbicacion());
            existing.setCorreo(a.getCorreo());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (repo.existsById(id)) { repo.deleteById(id); return ResponseEntity.noContent().build(); }
        return ResponseEntity.notFound().build();
    }
}

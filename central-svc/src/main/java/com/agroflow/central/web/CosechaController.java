package com.agroflow.central.web;

import com.agroflow.central.model.Cosecha;
import com.agroflow.central.repo.CosechaRepo;
import com.agroflow.central.repo.AgricultorRepo;
import com.agroflow.central.messaging.EventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/cosechas")
public class CosechaController {

    private final CosechaRepo cosechaRepo;
    private final AgricultorRepo agricultorRepo;
    private final EventPublisher publisher;

    public CosechaController(CosechaRepo cr, AgricultorRepo ar, EventPublisher pub) {
        this.cosechaRepo = cr;
        this.agricultorRepo = ar;
        this.publisher = pub;
    }

    // ---------- GETs ----------
    @GetMapping
    public List<Cosecha> listar() {
        return cosechaRepo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cosecha> porId(@PathVariable("id") UUID id) {
        return cosechaRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------- POST----------
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Cosecha c) {
        if (c.getAgricultorId() == null || !agricultorRepo.existsById(c.getAgricultorId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Agricultor no existe"));
        }
        if (c.getToneladas() == null || c.getToneladas() < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Toneladas inválidas"));
        }
        Cosecha saved = cosechaRepo.save(c);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cosecha_id", saved.getCosechaId().toString());
        payload.put("producto", saved.getProducto());
        payload.put("toneladas", saved.getToneladas());
        publisher.publishNuevaCosecha(payload);

        return ResponseEntity.ok(saved);
    }

    // ---------- PUT (nota el nombre explícito del path variable) ----------
    @PutMapping("/{id}/estado")
    public ResponseEntity<?> updateEstado(@PathVariable("id") UUID id,
                                          @RequestBody Map<String, Object> body) {
        return cosechaRepo.findById(id).map(c -> {
            String estado = (String) body.getOrDefault("estado", "REGISTRADA");
            c.setEstado(estado);
            if (body.containsKey("factura_id")) {
                try {
                    c.setFacturaId(UUID.fromString(body.get("factura_id").toString()));
                } catch (Exception ignored) {}
            }
            return ResponseEntity.ok(cosechaRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }
}

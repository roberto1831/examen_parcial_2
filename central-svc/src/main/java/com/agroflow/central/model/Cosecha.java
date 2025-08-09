package com.agroflow.central.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cosechas")
public class Cosecha {
    @Id
    @Column(name = "cosecha_id", nullable = false, unique = true)
    private UUID cosechaId = UUID.randomUUID();

    @Column(name = "agricultor_id", nullable = false)
    private UUID agricultorId;

    @Column(nullable = false, length = 50)
    private String producto;

    @Column(nullable = false)
    private Double toneladas;

    @Column(nullable = false, length = 20)
    private String estado = "REGISTRADA";

    private OffsetDateTime creadoEn = OffsetDateTime.now();

    private UUID facturaId;

    public UUID getCosechaId() { return cosechaId; }
    public void setCosechaId(UUID id) { this.cosechaId = id; }
    public UUID getAgricultorId() { return agricultorId; }
    public void setAgricultorId(UUID agricultorId) { this.agricultorId = agricultorId; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public Double getToneladas() { return toneladas; }
    public void setToneladas(Double toneladas) { this.toneladas = toneladas; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
    public UUID getFacturaId() { return facturaId; }
    public void setFacturaId(UUID facturaId) { this.facturaId = facturaId; }
}

package com.agroflow.central.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "agricultores")
public class Agricultor {
    @Id
    @Column(name = "agricultor_id", nullable = false, unique = true)
    private UUID agricultorId = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String finca;

    @Column(nullable = false, length = 100)
    private String ubicacion;

    @Column(nullable = false, length = 150, unique = true)
    private String correo;

    public UUID getAgricultorId() { return agricultorId; }
    public void setAgricultorId(UUID id) { this.agricultorId = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getFinca() { return finca; }
    public void setFinca(String finca) { this.finca = finca; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}

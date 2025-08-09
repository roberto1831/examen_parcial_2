package com.agroflow.central.repo;

import com.agroflow.central.model.Cosecha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CosechaRepo extends JpaRepository<Cosecha, UUID> { }

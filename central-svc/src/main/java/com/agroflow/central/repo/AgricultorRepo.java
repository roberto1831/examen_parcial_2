package com.agroflow.central.repo;

import com.agroflow.central.model.Agricultor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgricultorRepo extends JpaRepository<Agricultor, UUID> { }

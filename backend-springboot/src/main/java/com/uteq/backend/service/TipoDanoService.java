package com.uteq.backend.service;

import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.entity.TipoDano;
import com.uteq.backend.repository.TipoDanoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TipoDanoService {

    private final TipoDanoRepository tipoDanoRepo;

    @Transactional(readOnly = true)
    public List<TipoDanoDTO> listarTodos() {
        return tipoDanoRepo.findAll().stream()
                .map(t -> new TipoDanoDTO(t.getId(), t.getNombre(), t.getPrecio()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TipoDanoDTO> listarActivos() {
        return tipoDanoRepo.findByActivoTrue().stream()
                .map(t -> new TipoDanoDTO(t.getId(), t.getNombre(), t.getPrecio()))
                .toList();
    }

    @Transactional
    public TipoDanoDTO crear(String nombre, java.math.BigDecimal precio) {
        if (tipoDanoRepo.findByNombre(nombre).isPresent()) {
            throw new IllegalArgumentException("Ya existe un tipo de daño con el nombre: " + nombre);
        }
        TipoDano tipo = new TipoDano();
        tipo.setNombre(nombre);
        tipo.setPrecio(precio);
        tipo.setActivo(true);
        TipoDano guardado = tipoDanoRepo.save(tipo);
        return new TipoDanoDTO(guardado.getId(), guardado.getNombre(), guardado.getPrecio());
    }

    @Transactional
    public TipoDanoDTO actualizar(Integer id, String nombre, java.math.BigDecimal precio) {
        TipoDano tipo = tipoDanoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        tipoDanoRepo.findByNombre(nombre)
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> {
                    throw new IllegalArgumentException("Ya existe otro tipo de daño con el nombre: " + nombre);
                });
        tipo.setNombre(nombre);
        tipo.setPrecio(precio);
        TipoDano guardado = tipoDanoRepo.save(tipo);
        return new TipoDanoDTO(guardado.getId(), guardado.getNombre(), guardado.getPrecio());
    }

    @Transactional
    public void eliminar(Integer id) {
        TipoDano tipo = tipoDanoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        tipo.setActivo(false);
        tipoDanoRepo.save(tipo);
    }
}

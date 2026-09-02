package com.uteq.backend.service;

import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.entity.CategoriaDano;
import com.uteq.backend.entity.TipoDano;
import com.uteq.backend.repository.CategoriaDanoRepository;
import com.uteq.backend.repository.TipoDanoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TipoDanoService {

    private final TipoDanoRepository tipoDanoRepo;
    private final CategoriaDanoRepository categoriaDanoRepo;

    private TipoDanoDTO toDTO(TipoDano t) {
        return new TipoDanoDTO(t.getId(), t.getNombre(),
                t.getCategoria() != null ? t.getCategoria().getId() : null,
                t.getCategoria() != null ? t.getCategoria().getNombre() : null,
                t.getTipoCosto(), t.getValor());
    }

    @Transactional(readOnly = true)
    public List<TipoDanoDTO> listarTodos() {
        return tipoDanoRepo.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<TipoDanoDTO> listarActivos() {
        return tipoDanoRepo.findByActivoTrue().stream().map(this::toDTO).toList();
    }

    @Transactional
    public TipoDanoDTO crear(String nombre, Integer categoriaId, String tipoCosto, BigDecimal valor) {
        if (tipoDanoRepo.findByNombre(nombre).isPresent()) {
            throw new IllegalArgumentException("Ya existe un tipo de daño con el nombre: " + nombre);
        }
        CategoriaDano cat = categoriaDanoRepo.findById(categoriaId)
                .orElseThrow(() -> new EntityNotFoundException("Categoría de daño no encontrada: " + categoriaId));
        validar(tipoCosto, valor);
        TipoDano tipo = new TipoDano();
        tipo.setNombre(nombre);
        tipo.setCategoria(cat);
        tipo.setTipoCosto(tipoCosto);
        tipo.setValor(valor);
        tipo.setActivo(true);
        TipoDano guardado = tipoDanoRepo.save(tipo);
        return toDTO(guardado);
    }

    @Transactional
    public TipoDanoDTO actualizar(Integer id, String nombre, Integer categoriaId, String tipoCosto, BigDecimal valor) {
        TipoDano tipo = tipoDanoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        tipoDanoRepo.findByNombre(nombre)
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> { throw new IllegalArgumentException("Ya existe otro tipo de daño con el nombre: " + nombre); });
        CategoriaDano cat = categoriaDanoRepo.findById(categoriaId)
                .orElseThrow(() -> new EntityNotFoundException("Categoría de daño no encontrada: " + categoriaId));
        validar(tipoCosto, valor);
        tipo.setNombre(nombre);
        tipo.setCategoria(cat);
        tipo.setTipoCosto(tipoCosto);
        tipo.setValor(valor);
        TipoDano guardado = tipoDanoRepo.save(tipo);
        return toDTO(guardado);
    }

    private void validar(String tipoCosto, BigDecimal valor) {
        if (!"FIJO".equals(tipoCosto) && !"PORCENTAJE".equals(tipoCosto)) {
            throw new IllegalArgumentException("tipoCosto debe ser FIJO o PORCENTAJE");
        }
        if (valor == null || valor.signum() < 0) throw new IllegalArgumentException("valor debe ser >=0");
        if ("PORCENTAJE".equals(tipoCosto) && valor.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("porcentaje no puede superar 100");
    }

    @Transactional
    public void eliminar(Integer id) {
        TipoDano tipo = tipoDanoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        tipo.setActivo(false);
        tipoDanoRepo.save(tipo);
    }
}

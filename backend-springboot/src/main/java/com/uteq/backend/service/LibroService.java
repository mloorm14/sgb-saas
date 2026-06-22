package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibroService {

    private final LibroRepository libroRepo;
    private final EditorialRepository editorialRepo;
    private final IdiomaRepository idiomaRepo;
    private final EstadoLibroRepository estadoRepo;

    public LibroService(LibroRepository libroRepo,
                        EditorialRepository editorialRepo,
                        IdiomaRepository idiomaRepo,
                        EstadoLibroRepository estadoRepo) {
        this.libroRepo     = libroRepo;
        this.editorialRepo = editorialRepo;
        this.idiomaRepo    = idiomaRepo;
        this.estadoRepo    = estadoRepo;
    }

    @Cacheable("libros")
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listar(Pageable pageable) {
        return libroRepo.findByActivoTrue(pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public LibroResponseDTO buscarPorId(Long id) {
        return libroRepo.findById(id)
                .filter(Libro::getActivo)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Libro no encontrado con id: " + id));
    }

    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO crear(LibroRequestDTO dto) {
        if (libroRepo.existsByIsbn(dto.isbn())) {
            throw new IllegalArgumentException(
                    "ISBN ya registrado: " + dto.isbn());
        }
        validarStock(dto.stockTotal(), dto.stockDisponible());
        return toDTO(libroRepo.save(fromDTO(dto)));
    }

    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO actualizar(Long id, LibroRequestDTO dto) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Libro no encontrado con id: " + id));
        if (libroRepo.existsByIsbnAndIdNot(dto.isbn(), id)) {
            throw new IllegalArgumentException(
                    "ISBN ya usado por otro libro: " + dto.isbn());
        }
        validarStock(dto.stockTotal(), dto.stockDisponible());

        libro.setTitulo(dto.titulo());
        libro.setIsbn(dto.isbn());
        libro.setResumen(dto.resumen());
        libro.setPortadaUrl(dto.portadaUrl());
        libro.setAnioPublicacion(dto.anioPublicacion().shortValue());
        libro.setStockTotal(dto.stockTotal().shortValue());
        libro.setStockDisponible(dto.stockDisponible().shortValue());
        libro.setEditorial(dto.editorialId() != null ? editorialRepo.getReferenceById(dto.editorialId()) : null);
        libro.setIdioma(dto.idiomaId() != null ? idiomaRepo.getReferenceById(dto.idiomaId()) : null);
        libro.setEstado(dto.estadoId() != null ? estadoRepo.getReferenceById(dto.estadoId()) : null);

        return toDTO(libroRepo.save(libro));
    }

    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public void eliminar(Long id) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Libro no encontrado con id: " + id));
        libro.setActivo(false);
        libroRepo.save(libro);
    }

    private void validarStock(Integer stockTotal, Integer stockDisponible) {
        if (stockTotal == null || stockDisponible == null) return;
        if (stockDisponible > stockTotal) {
            throw new IllegalArgumentException(
                    "El stock disponible no puede ser mayor al stock total");
        }
    }

    private LibroResponseDTO toDTO(Libro l) {
        return new LibroResponseDTO(
                l.getId(),
                l.getTitulo(),
                l.getIsbn(),
                l.getResumen(),
                l.getPortadaUrl(),
                l.getAnioPublicacion() != null ? l.getAnioPublicacion().intValue() : null,
                l.getEditorial()  != null ? l.getEditorial().getId()     : null,
                l.getEditorial()  != null ? l.getEditorial().getNombre() : null,
                l.getIdioma()     != null ? l.getIdioma().getId()        : null,
                l.getIdioma()     != null ? l.getIdioma().getNombre()    : null,
                l.getEstado()     != null ? l.getEstado().getId()        : null,
                l.getEstado()     != null ? l.getEstado().getNombre()    : null,
                l.getStockTotal()      != null ? l.getStockTotal().intValue()      : null,
                l.getStockDisponible() != null ? l.getStockDisponible().intValue() : null,
                l.getActivo(),
                l.getCreadoEn()
        );
    }

    private Libro fromDTO(LibroRequestDTO dto) {
        Libro l = new Libro();
        l.setTitulo(dto.titulo());
        l.setIsbn(dto.isbn());
        l.setResumen(dto.resumen());
        l.setPortadaUrl(dto.portadaUrl());
        l.setAnioPublicacion(dto.anioPublicacion().shortValue());
        l.setStockTotal(dto.stockTotal().shortValue());
        l.setStockDisponible(dto.stockDisponible().shortValue());
        l.setEditorial(editorialRepo.getReferenceById(dto.editorialId()));
        l.setIdioma(idiomaRepo.getReferenceById(dto.idiomaId()));
        l.setEstado(estadoRepo.getReferenceById(dto.estadoId()));
        return l;
    }
}
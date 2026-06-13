package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LibroService {

    private final LibroRepository libroRepo;
    private final EditorialRepository editorialRepo;
    private final IdiomaRepository idiomaRepo;
    private final EstadoLibroRepository estadoRepo;

    public LibroService(LibroRepository libroRepo,
                        EditorialRepository editorialRepo,
                        IdiomaRepository idiomaRepo,
                        EstadoLibroRepository estadoRepo) {
        this.libroRepo    = libroRepo;
        this.editorialRepo = editorialRepo;
        this.idiomaRepo   = idiomaRepo;
        this.estadoRepo   = estadoRepo;
    }

    // ── Listar paginado (solo activos) ────────────────────
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listar(Pageable pageable) {
        return libroRepo.findByActivoTrue(pageable)
                .map(this::toDTO);
    }

    // ── Buscar por ID ─────────────────────────────────────
    @Transactional(readOnly = true)
    public LibroResponseDTO buscarPorId(Long id) {
        return libroRepo.findById(id)
                .filter(Libro::getActivo)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Libro no encontrado con id: " + id));
    }

    // ── Crear ─────────────────────────────────────────────
    public LibroResponseDTO crear(LibroRequestDTO dto) {
        if (libroRepo.existsByIsbn(dto.isbn())) {
            throw new IllegalArgumentException(
                    "ISBN ya registrado: " + dto.isbn());
        }
        Libro libro = fromDTO(dto);
        return toDTO(libroRepo.save(libro));
    }

    // ── Actualizar ────────────────────────────────────────
    public LibroResponseDTO actualizar(Long id, LibroRequestDTO dto) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Libro no encontrado con id: " + id));
        if (libroRepo.existsByIsbnAndIdNot(dto.isbn(), id)) {
            throw new IllegalArgumentException(
                    "ISBN ya usado por otro libro: " + dto.isbn());
        }
        libro.setTitulo(dto.titulo());
        libro.setIsbn(dto.isbn());
        libro.setAutor(dto.autor());
        libro.setAnioPublicacion(dto.anioPublicacion());
        if (dto.editorialId() != null)
            libro.setEditorial(editorialRepo.getReferenceById(dto.editorialId()));
        if (dto.idiomaId() != null)
            libro.setIdioma(idiomaRepo.getReferenceById(dto.idiomaId()));
        if (dto.estadoId() != null)
            libro.setEstado(estadoRepo.getReferenceById(dto.estadoId()));
        return toDTO(libroRepo.save(libro));
    }

    // ── Soft delete ───────────────────────────────────────
    public void eliminar(Long id) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Libro no encontrado con id: " + id));
        libro.setActivo(false);
        libroRepo.save(libro);
    }

    // ── Mapeo entidad → DTO ───────────────────────────────
    private LibroResponseDTO toDTO(Libro l) {
        return new LibroResponseDTO(
                l.getId(),
                l.getTitulo(),
                l.getIsbn(),
                l.getAutor(),
                l.getAnioPublicacion(),
                l.getEditorial()  != null ? l.getEditorial().getNombre()  : null,
                l.getIdioma()     != null ? l.getIdioma().getNombre()     : null,
                l.getEstado()     != null ? l.getEstado().getNombre()     : null,
                l.getActivo(),
                l.getCreadoEn()
        );
    }

    // ── Mapeo DTO → entidad ───────────────────────────────
    private Libro fromDTO(LibroRequestDTO dto) {
        Libro l = new Libro();
        l.setTitulo(dto.titulo());
        l.setIsbn(dto.isbn());
        l.setAutor(dto.autor());
        l.setAnioPublicacion(dto.anioPublicacion());
        if (dto.editorialId() != null)
            l.setEditorial(editorialRepo.getReferenceById(dto.editorialId()));
        if (dto.idiomaId() != null)
            l.setIdioma(idiomaRepo.getReferenceById(dto.idiomaId()));
        if (dto.estadoId() != null)
            l.setEstado(estadoRepo.getReferenceById(dto.estadoId()));
        return l;
    }
}
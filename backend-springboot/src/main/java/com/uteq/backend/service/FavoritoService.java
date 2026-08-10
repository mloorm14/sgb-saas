package com.uteq.backend.service;

import com.uteq.backend.dto.FavoritoResponseDTO;
import com.uteq.backend.entity.Favorito;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.FavoritoRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Módulo 9.2 del roadmap. Sin @PreAuthorize a nivel de método (eso vive en
// FavoritoController, defensa en profundidad) -- acá solo se resuelve
// "de quién es este favorito": un LECTOR únicamente puede marcar/ver sus
// propios favoritos, resuelto siempre desde el Authentication autenticado,
// nunca de un usuarioId que venga en el request (mismo criterio que
// PrestamoService.resolverIdPorCorreo/validarAccesoUsuario).
@Service
public class FavoritoService {

    private static final String LIBRO_NO_ENCONTRADO = "Libro no encontrado con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado con correo: ";
    private static final String FAVORITO_NO_ENCONTRADO = "El libro %d no está en favoritos del usuario autenticado.";

    private final FavoritoRepository favoritoRepo;
    private final LibroRepository libroRepo;
    private final UsuarioRepository usuarioRepo;

    public FavoritoService(FavoritoRepository favoritoRepo,
                            LibroRepository libroRepo,
                            UsuarioRepository usuarioRepo) {
        this.favoritoRepo = favoritoRepo;
        this.libroRepo = libroRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional
    public FavoritoResponseDTO agregar(Long libroId, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        Libro libro = libroRepo.findById(libroId)
                .orElseThrow(() -> new EntityNotFoundException(LIBRO_NO_ENCONTRADO + libroId));

        if (favoritoRepo.existsByUsuarioIdAndLibroId(usuarioId, libroId)) {
            // Idempotente a propósito: marcar dos veces el mismo libro no
            // es un error del cliente (el botón "★" del frontend no tiene
            // por qué saber si ya estaba marcado antes de hacer clic),
            // simplemente devuelve el estado actual.
            Favorito existente = favoritoRepo.findByUsuarioId(usuarioId).stream()
                    .filter(f -> f.getLibroId().equals(libroId))
                    .findFirst()
                    .orElseThrow();
            return toDTO(existente, libro.getTitulo());
        }

        Favorito favorito = favoritoRepo.save(new Favorito(usuarioId, libroId));
        return toDTO(favorito, libro.getTitulo());
    }

    @Transactional
    public void quitar(Long libroId, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        if (!favoritoRepo.existsByUsuarioIdAndLibroId(usuarioId, libroId)) {
            throw new EntityNotFoundException(String.format(FAVORITO_NO_ENCONTRADO, libroId));
        }
        favoritoRepo.deleteByUsuarioIdAndLibroId(usuarioId, libroId);
    }

    @Transactional(readOnly = true)
    public List<FavoritoResponseDTO> listarPropios(Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        return favoritoRepo.findByUsuarioId(usuarioId).stream()
                .map(f -> toDTO(f, tituloDe(f.getLibroId())))
                .toList();
    }

    private String tituloDe(Long libroId) {
        return libroRepo.findById(libroId).map(Libro::getTitulo).orElse(null);
    }

    private Long resolverIdPorCorreo(String correo) {
        return usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo))
                .getId();
    }

    private FavoritoResponseDTO toDTO(Favorito f, String tituloLibro) {
        return new FavoritoResponseDTO(f.getUsuarioId(), f.getLibroId(), tituloLibro, f.getAgregadoEn());
    }
}

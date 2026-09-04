package com.uteq.backend.service;

import com.uteq.backend.dto.FavoritoResponseDTO;
import com.uteq.backend.entity.Favorito;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.FavoritoRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {

    @Mock FavoritoRepository favoritoRepo;
    @Mock LibroRepository libroRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock Authentication authentication;

    @InjectMocks FavoritoService favoritoService;

    // ── Test 1: agregar un libro nuevo a favoritos ────────
    @Test
    void agregar_cuandoLibroExisteYNoEsFavoritoAun_loGuarda() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(usuarioRepo.findByCorreo("lector@correo.com")).willReturn(Optional.of(usuarioConId(7L)));
        given(libroRepo.findById(1L)).willReturn(Optional.of(libroConTitulo("Clean Code")));
        given(favoritoRepo.existsByUsuarioIdAndLibroId(7L, 1L)).willReturn(false);
        given(favoritoRepo.save(org.mockito.ArgumentMatchers.any())).willReturn(new Favorito(7L, 1L));

        FavoritoResponseDTO resultado = favoritoService.agregar(1L, authentication);

        assertThat(resultado.usuarioId()).isEqualTo(7L);
        assertThat(resultado.libroId()).isEqualTo(1L);
        assertThat(resultado.tituloLibro()).isEqualTo("Clean Code");
    }

    // ── Test 2: agregar un libro que no existe lanza 404 ──
    @Test
    void agregar_cuandoLibroNoExiste_lanzaEntityNotFound() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(usuarioRepo.findByCorreo("lector@correo.com")).willReturn(Optional.of(usuarioConId(7L)));
        given(libroRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> favoritoService.agregar(99L, authentication))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── Test 3: agregar un favorito ya marcado es idempotente ──
    // (no lanza excepcion, devuelve el existente en vez de duplicar)
    @Test
    void agregar_cuandoYaEsFavorito_noDuplicaYDevuelveElExistente() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(usuarioRepo.findByCorreo("lector@correo.com")).willReturn(Optional.of(usuarioConId(7L)));
        given(libroRepo.findById(1L)).willReturn(Optional.of(libroConTitulo("Clean Code")));
        given(favoritoRepo.existsByUsuarioIdAndLibroId(7L, 1L)).willReturn(true);
        given(favoritoRepo.findByUsuarioId(7L)).willReturn(List.of(new Favorito(7L, 1L)));

        FavoritoResponseDTO resultado = favoritoService.agregar(1L, authentication);

        assertThat(resultado.libroId()).isEqualTo(1L);
        verify(favoritoRepo, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ── Test 4: quitar un favorito que no existe lanza 404 ──
    @Test
    void quitar_cuandoNoEsFavorito_lanzaEntityNotFound() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(usuarioRepo.findByCorreo("lector@correo.com")).willReturn(Optional.of(usuarioConId(7L)));
        given(favoritoRepo.existsByUsuarioIdAndLibroId(7L, 1L)).willReturn(false);

        assertThatThrownBy(() -> favoritoService.quitar(1L, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 5: listarPropios solo devuelve favoritos del usuario
    // autenticado ────────────────────────────────────────
    @Test
    void listarPropios_devuelveSoloLosDelUsuarioAutenticado() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(usuarioRepo.findByCorreo("lector@correo.com")).willReturn(Optional.of(usuarioConId(7L)));
        given(favoritoRepo.findByUsuarioId(7L)).willReturn(List.of(new Favorito(7L, 1L)));
        given(libroRepo.findById(1L)).willReturn(Optional.of(libroConTitulo("Clean Code")));

        List<FavoritoResponseDTO> resultado = favoritoService.listarPropios(authentication);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).usuarioId()).isEqualTo(7L);
    }

    // ── Helpers ───────────────────────────────────────────
    private Usuario usuarioConId(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Libro libroConTitulo(String titulo) {
        Libro libro = new Libro();
        libro.setId(1L);
        libro.setTitulo(titulo);
        return libro;
    }
}

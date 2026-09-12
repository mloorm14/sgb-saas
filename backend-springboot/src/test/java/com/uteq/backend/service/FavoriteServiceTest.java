package com.uteq.backend.service;

import com.uteq.backend.dto.FavoriteResponseDTO;
import com.uteq.backend.entity.Favorite;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.FavoriteRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.UserRepository;
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
class FavoriteServiceTest {

    @Mock FavoriteRepository favoriteRepo;
    @Mock BookRepository bookRepo;
    @Mock UserRepository userRepo;
    @Mock Authentication authentication;

    @InjectMocks FavoriteService favoriteService;

    // ── Test 1: agregar un libro nuevo a favoritos ────────
    @Test
    void agregar_cuandoBookExisteYNotEsFavoriteAun_loGuarda() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        given(bookRepo.findById(1L)).willReturn(Optional.of(bookWithTitle("Clean Code")));
        given(favoriteRepo.existsByUserIdAndBookId(7L, 1L)).willReturn(false);
        given(favoriteRepo.save(org.mockito.ArgumentMatchers.any())).willReturn(new Favorite(7L, 1L));

        FavoriteResponseDTO result = favoriteService.agregar(1L, authentication);

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.bookId()).isEqualTo(1L);
        assertThat(result.titleBook()).isEqualTo("Clean Code");
    }

    // ── Test 2: agregar un libro que no existe lanza 404 ──
    @Test
    void agregar_cuandoBookNotExiste_lanzaEntityNotFound() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        given(bookRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.agregar(99L, authentication))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── Test 3: agregar un favorito ya marcado es idempotente ──
    // (no lanza excepcion, devuelve el existente en vez de duplicar)
    @Test
    void agregar_cuandoYaEsFavorite_notDuplicaYDevuelveExisting() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        given(bookRepo.findById(1L)).willReturn(Optional.of(bookWithTitle("Clean Code")));
        given(favoriteRepo.existsByUserIdAndBookId(7L, 1L)).willReturn(true);
        given(favoriteRepo.findByUserId(7L)).willReturn(List.of(new Favorite(7L, 1L)));

        FavoriteResponseDTO result = favoriteService.agregar(1L, authentication);

        assertThat(result.bookId()).isEqualTo(1L);
        verify(favoriteRepo, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ── Test 4: quitar un favorito que no existe lanza 404 ──
    @Test
    void quitar_cuandoNotEsFavorite_lanzaEntityNotFound() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        given(favoriteRepo.existsByUserIdAndBookId(7L, 1L)).willReturn(false);

        assertThatThrownBy(() -> favoriteService.quitar(1L, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 5: listarPropios solo devuelve favoritos del usuario
    // autenticado ────────────────────────────────────────
    @Test
    void listOwns_devuelveSoloUserAutenticado() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        given(favoriteRepo.findByUserId(7L)).willReturn(List.of(new Favorite(7L, 1L)));
        given(bookRepo.findById(1L)).willReturn(Optional.of(bookWithTitle("Clean Code")));

        List<FavoriteResponseDTO> result = favoriteService.listOwns(authentication);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(7L);
    }

    // ── Helpers ───────────────────────────────────────────
    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Book bookWithTitle(String title) {
        Book book = new Book();
        book.setId(1L);
        book.setTitle(title);
        return book;
    }
}

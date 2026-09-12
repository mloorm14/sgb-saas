package com.uteq.backend.service;

import com.uteq.backend.dto.FavoriteResponseDTO;
import com.uteq.backend.entity.Favorite;
import com.uteq.backend.entity.Book;
import com.uteq.backend.repository.FavoriteRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

// Favoritos del usuario autenticado: el dueño se resuelve siempre desde el Authentication.
@Service
public class FavoriteService {

    private static final String LIBRO_NO_ENCONTRADO = "Libro no encontrado con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado con correo: ";
    private static final String FAVORITO_NO_ENCONTRADO = "El libro %d no está en favoritos del usuario autenticado.";

    private final FavoriteRepository favoriteRepo;
    private final BookRepository bookRepo;
    private final UserRepository userRepo;

    public FavoriteService(FavoriteRepository favoriteRepo,
                            BookRepository bookRepo,
                            UserRepository userRepo) {
        this.favoriteRepo = favoriteRepo;
        this.bookRepo = bookRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    /**
     * Handles agregar.
     *
     * @param bookId numeric identifier used to scope this agregar
     * @param authentication authentication of the caller used to scope this agregar
     * @return favorite Response data transfer object reflecting the state after the operation
     * @throws EntityNotFoundException when the agregar cannot be processed with the given input
     */
    public FavoriteResponseDTO agregar(Long bookId, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException(LIBRO_NO_ENCONTRADO + bookId));

        if (favoriteRepo.existsByUserIdAndBookId(userId, bookId)) {
            // Idempotente: marcar dos veces devuelve el estado actual.
            Favorite existing = favoriteRepo.findByUserId(userId).stream()
                    .filter(f -> f.getBookId().equals(bookId))
                    .findFirst()
                    .orElseThrow();
            return toDTO(existing, book.getTitle());
        }

        Favorite favorite = favoriteRepo.save(new Favorite(userId, bookId));
        return toDTO(favorite, book.getTitle());
    }

    @Transactional
    /**
     * Handles quitar.
     *
     * @param bookId numeric identifier used to scope this quitar
     * @param authentication authentication of the caller used to scope this quitar
     * @throws EntityNotFoundException when the quitar cannot be processed with the given input
     */
    public void quitar(Long bookId, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());
        if (!favoriteRepo.existsByUserIdAndBookId(userId, bookId)) {
            throw new EntityNotFoundException(String.format(FAVORITO_NO_ENCONTRADO, bookId));
        }
        favoriteRepo.deleteByUserIdAndBookId(userId, bookId);
    }

    @Transactional(readOnly = true)
    /**
     * Lists favorite Response DTO records.
     *
     * @param authentication authentication of the caller used to scope this favorite Response DTO records
     * @return list of favorite Response data transfer object matching the requested criteria
     */
    public List<FavoriteResponseDTO> listOwns(Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());
        return favoriteRepo.findByUserId(userId).stream()
                .map(f -> toDTO(f, title(f.getBookId())))
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Lists favorite Response DTO records.
     *
     * @param authentication authentication of the caller used to scope this favorite Response DTO records
     * @param pageable pagination information used to scope this favorite Response DTO records
     * @return page of favorite Response data transfer object for the requested pagination
     */
    public Page<FavoriteResponseDTO> listOwnsPaginado(Authentication authentication, Pageable pageable) {
        Long userId = resolveIdByEmail(authentication.getName());
        return favoriteRepo.findByUserId(userId, pageable)
                .map(f -> toDTO(f, title(f.getBookId())));
    }

    private String title(Long bookId) {
        return bookRepo.findById(bookId).map(Book::getTitle).orElse(null);
    }

    private Long resolveIdByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email))
                .getId();
    }

    private FavoriteResponseDTO toDTO(Favorite f, String titleBook) {
        return new FavoriteResponseDTO(f.getUserId(), f.getBookId(), titleBook, f.getAgregado());
    }
}

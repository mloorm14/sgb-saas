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
     * Procesa agregar y devuelve el resultado calculado por el backend.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
     * Ejecuta quitar aplicando las validaciones necesarias del proceso.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
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
     * Consulta list owns usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<FavoriteResponseDTO> listOwns(Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());
        return favoriteRepo.findByUserId(userId).stream()
                .map(f -> toDTO(f, title(f.getBookId())))
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Consulta list owns paginado usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return pagina de resultados que coincide con los filtros y la paginacion solicitada
     */
    public Page<FavoriteResponseDTO> listOwnsPaginated(Authentication authentication, Pageable pageable) {
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

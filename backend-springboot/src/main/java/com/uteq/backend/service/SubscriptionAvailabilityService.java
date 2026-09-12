package com.uteq.backend.service;

import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.SubscriptionAvailability;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.SubscriptionAvailabilityRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SubscriptionAvailabilityService {

    private final SubscriptionAvailabilityRepository subscriptionRepo;
    private final UserRepository userRepo;
    private final BookRepository bookRepo;
    private final NotificationService notificationService;

    public SubscriptionAvailabilityService(SubscriptionAvailabilityRepository subscriptionRepo,
                                            UserRepository userRepo,
                                            BookRepository bookRepo,
                                            NotificationService notificationService) {
        this.subscriptionRepo = subscriptionRepo;
        this.userRepo = userRepo;
        this.bookRepo = bookRepo;
        this.notificationService = notificationService;
    }

    @Transactional
    /**
     * Ejecuta suscribir aplicando las validaciones necesarias del proceso.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     */
    public void suscribir(Long userId, Long bookId) {
        userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + userId));
        Book book = bookRepo.findById(bookId).orElseThrow(() -> new EntityNotFoundException("Libro no encontrado: " + bookId));
        if (subscriptionRepo.existsByUserIdAndBookId(userId, bookId)) {
            return;
        }
        SubscriptionAvailability s = new SubscriptionAvailability();
        s.setUserId(userId);
        s.setBookId(bookId);
        s.setCreated(OffsetDateTime.now());
        subscriptionRepo.save(s);
        // Si ya esta disponible, notificar inmediato
        if (book.getStockAvailable() != null && book.getStockAvailable() > 0) {
            notificationService.notifyBookAvailable(userId, bookId, book.getTitle());
        }
    }

    @Transactional
    /**
     * Ejecuta desuscribir aplicando las validaciones necesarias del proceso.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     */
    public void desuscribir(Long userId, Long bookId) {
        subscriptionRepo.deleteByUserIdAndBookId(userId, bookId);
    }

    @Transactional(readOnly = true)
    /**
     * Consulta list books ids usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<Long> listBooksIds(Long userId) {
        return subscriptionRepo.findByUserId(userId).stream().map(SubscriptionAvailability::getBookId).toList();
    }

    @Transactional
    /**
     * Envia notify disponibles usando los datos y destinatarios recibidos.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     */
    public void notifyDisponibles(Long bookId) {
        Book book = bookRepo.findById(bookId).orElseThrow(() -> new EntityNotFoundException("Libro no encontrado: " + bookId));
        if (book.getStockAvailable() == null || book.getStockAvailable() <= 0) return;
        List<SubscriptionAvailability> subs = subscriptionRepo.findByBookId(bookId);
        for (SubscriptionAvailability s : subs) {
            notificationService.notifyBookAvailable(s.getUserId(), bookId, book.getTitle());
            subscriptionRepo.delete(s);
        }
    }
}

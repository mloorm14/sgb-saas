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
     * Handles suscribir.
     *
     * @param userId numeric identifier used to scope this suscribir
     * @param bookId numeric identifier used to scope this suscribir
     * @throws EntityNotFoundException when the suscribir cannot be processed with the given input
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
     * Handles desuscribir.
     *
     * @param userId numeric identifier used to scope this desuscribir
     * @param bookId numeric identifier used to scope this desuscribir
     */
    public void desuscribir(Long userId, Long bookId) {
        subscriptionRepo.deleteByUserIdAndBookId(userId, bookId);
    }

    @Transactional(readOnly = true)
    /**
     * Lists Long records.
     *
     * @param userId numeric identifier used to scope this Long records
     * @return list of Long matching the requested criteria
     */
    public List<Long> listBooksIds(Long userId) {
        return subscriptionRepo.findByUserId(userId).stream().map(SubscriptionAvailability::getBookId).toList();
    }

    @Transactional
    /**
     * Notifies subscription availability.
     *
     * @param bookId numeric identifier used to scope this subscription availability
     * @throws EntityNotFoundException when the subscription availability cannot be processed with the given input
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

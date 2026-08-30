package com.uteq.backend.service;

import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.SuscripcionDisponibilidad;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.SuscripcionDisponibilidadRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SuscripcionDisponibilidadService {

    private final SuscripcionDisponibilidadRepository suscripcionRepo;
    private final UsuarioRepository usuarioRepo;
    private final LibroRepository libroRepo;
    private final NotificacionService notificacionService;

    public SuscripcionDisponibilidadService(SuscripcionDisponibilidadRepository suscripcionRepo,
                                            UsuarioRepository usuarioRepo,
                                            LibroRepository libroRepo,
                                            NotificacionService notificacionService) {
        this.suscripcionRepo = suscripcionRepo;
        this.usuarioRepo = usuarioRepo;
        this.libroRepo = libroRepo;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public void suscribir(Long usuarioId, Long libroId) {
        usuarioRepo.findById(usuarioId).orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));
        Libro libro = libroRepo.findById(libroId).orElseThrow(() -> new EntityNotFoundException("Libro no encontrado: " + libroId));
        if (suscripcionRepo.existsByUsuarioIdAndLibroId(usuarioId, libroId)) {
            return;
        }
        SuscripcionDisponibilidad s = new SuscripcionDisponibilidad();
        s.setUsuarioId(usuarioId);
        s.setLibroId(libroId);
        s.setCreadoEn(OffsetDateTime.now());
        suscripcionRepo.save(s);
        // Si ya esta disponible, notificar inmediato
        if (libro.getStockDisponible() != null && libro.getStockDisponible() > 0) {
            notificacionService.notificarLibroDisponible(usuarioId, libroId, libro.getTitulo());
        }
    }

    @Transactional
    public void desuscribir(Long usuarioId, Long libroId) {
        suscripcionRepo.deleteByUsuarioIdAndLibroId(usuarioId, libroId);
    }

    @Transactional(readOnly = true)
    public List<Long> listarLibrosIds(Long usuarioId) {
        return suscripcionRepo.findByUsuarioId(usuarioId).stream().map(SuscripcionDisponibilidad::getLibroId).toList();
    }

    @Transactional
    public void notificarDisponibles(Long libroId) {
        Libro libro = libroRepo.findById(libroId).orElseThrow(() -> new EntityNotFoundException("Libro no encontrado: " + libroId));
        if (libro.getStockDisponible() == null || libro.getStockDisponible() <= 0) return;
        List<SuscripcionDisponibilidad> subs = suscripcionRepo.findByLibroId(libroId);
        for (SuscripcionDisponibilidad s : subs) {
            notificacionService.notificarLibroDisponible(s.getUsuarioId(), libroId, libro.getTitulo());
            suscripcionRepo.delete(s);
        }
    }
}

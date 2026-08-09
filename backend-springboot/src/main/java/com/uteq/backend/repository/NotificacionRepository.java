package com.uteq.backend.repository;

import com.uteq.backend.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // GET /notificaciones/usuario/{id} (lector solo ve las suyas, mismo
    // patrón de autorización que MultaController).
    Page<Notificacion> findByUsuarioId(Long usuarioId, Pageable pageable);

    // Usada por NotificacionVencimientoScheduler para no reenviar la misma
    // alerta de VENCIMIENTO sobre un préstamo que ya la tiene (evitar
    // duplicados si el scheduler corre más de una vez dentro de la ventana
    // de anticipación configurada).
    boolean existsByPrestamoIdAndTipoNotificacionId(Long prestamoId, Integer tipoNotificacionId);
}

package com.uteq.backend.repository;

import com.uteq.backend.entity.Prestamo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD elemental sobre {@code prestamos}. Solo consultas derivadas de una
 * sola tabla (sin joins) — cualquier lectura que combine préstamos con
 * libros/estados vive en {@link PrestamoProcedureRepository}
 * (fn_listar_prestamos_activos_por_usuario, fn_reporte_libros_mas_prestados).
 */
@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    Page<Prestamo> findByUsuarioId(Long usuarioId, Pageable pageable);

    Page<Prestamo> findByEstadoPrestamoId(Integer estadoId, Pageable pageable);
}

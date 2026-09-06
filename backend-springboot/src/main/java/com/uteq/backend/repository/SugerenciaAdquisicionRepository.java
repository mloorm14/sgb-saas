package com.uteq.backend.repository;

import com.uteq.backend.dto.SugerenciaAgrupadaDTO;
import com.uteq.backend.entity.SugerenciaAdquisicion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SugerenciaAdquisicionRepository extends JpaRepository<SugerenciaAdquisicion, Long> {

    Page<SugerenciaAdquisicion> findByUsuarioId(Long usuarioId, Pageable pageable);

    Page<SugerenciaAdquisicion> findByEstado(String estado, Pageable pageable);

    List<SugerenciaAdquisicion> findByIsbnAndEstado(String isbn, String estado);

    // Gestión por demanda: agrupa PENDIENTE con ISBN por libro. Sin ISBN
    // no hay cómo confirmar contra catálogo, así que se excluyen (siguen
    // visibles en /mias del lector y en el listado plano por estado).
    // El ORDER BY va explícito con COUNT(s) (no con Sort de Spring, que
    // calificaría "cantidad" contra la entidad y rompe con
    // UnknownPathException); el segundo término da orden estable para
    // que la paginación no duplique ni salte filas.
    @Query(value = "SELECT new com.uteq.backend.dto.SugerenciaAgrupadaDTO("
            + "s.isbn, MAX(s.titulo), MAX(s.autor), COUNT(s)) "
            + "FROM SugerenciaAdquisicion s "
            + "WHERE s.estado = 'PENDIENTE' AND s.isbn IS NOT NULL "
            + "GROUP BY s.isbn ORDER BY COUNT(s) DESC, s.isbn ASC",
            countQuery = "SELECT COUNT(DISTINCT s.isbn) FROM SugerenciaAdquisicion s "
                    + "WHERE s.estado = 'PENDIENTE' AND s.isbn IS NOT NULL")
    Page<SugerenciaAgrupadaDTO> findMasPedidosAgrupados(Pageable pageable);
}

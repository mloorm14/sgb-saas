package com.uteq.backend.repository;

import com.uteq.backend.dto.SuggestionGroupedDTO;
import com.uteq.backend.entity.SuggestionAcquisition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuggestionAcquisitionRepository extends JpaRepository<SuggestionAcquisition, Long> {

    Page<SuggestionAcquisition> findByUserId(Long userId, Pageable pageable);

    Page<SuggestionAcquisition> findByStatus(String status, Pageable pageable);

    List<SuggestionAcquisition> findByIsbnAndStatus(String isbn, String status);

    // Gestión por demanda: agrupa PENDIENTE con ISBN por libro. Sin ISBN
    // no hay cómo confirmar contra catálogo, así que se excluyen (siguen
    // visibles en /mias del lector y en el listado plano por estado).
    // El ORDER BY va explícito con COUNT(s) (no con Sort de Spring, que
    // calificaría "cantidad" contra la entidad y rompe con
    // UnknownPathException); el segundo término da orden estable para
    // que la paginación no duplique ni salte filas.
    @Query(value = "SELECT new com.uteq.backend.dto.SuggestionGroupedDTO("
            + "s.isbn, MAX(s.title), MAX(s.author), COUNT(s)) "
            + "FROM SuggestionAcquisition s "
            + "WHERE s.status = 'PENDIENTE' AND s.isbn IS NOT NULL "
            + "GROUP BY s.isbn ORDER BY COUNT(s) DESC, s.isbn ASC",
            countQuery = "SELECT COUNT(DISTINCT s.isbn) FROM SuggestionAcquisition s "
                    + "WHERE s.status = 'PENDIENTE' AND s.isbn IS NOT NULL")
    Page<SuggestionGroupedDTO> findMostPedidosAgrupados(Pageable pageable);
}

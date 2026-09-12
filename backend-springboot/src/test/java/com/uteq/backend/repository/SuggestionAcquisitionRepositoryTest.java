package com.uteq.backend.repository;

import com.uteq.backend.dto.SuggestionGroupedDTO;
import com.uteq.backend.entity.SuggestionAcquisition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

// Valida de verdad el JPQL de findMasPedidosAgrupados (constructor +
// GROUP BY + ORDER BY COUNT + countQuery + Pageable): los tests con mocks
// no parsean la query y así se nos escapó UnknownPathException a prod.
// Flyway apagado y H2 en modo PostgreSQL: las migraciones son
// PostgreSQL puro y H2 genera el schema desde las entidades
// (create-drop por defecto del slice).
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SuggestionAcquisitionRepositoryTest {

    @Autowired
    private SuggestionAcquisitionRepository repo;

    private SuggestionAcquisition pending(String isbn, String title) {
        SuggestionAcquisition s = new SuggestionAcquisition();
        s.setUserId(1L);
        s.setTitle(title);
        s.setAuthor("Autor");
        s.setIsbn(isbn);
        s.setStatus(SuggestionAcquisition.PENDIENTE);
        return repo.save(s);
    }

    @Test
    void findMostPedidosAgrupados_agrupaByIsbnOrdenadoByQuantity() {
        pending("9781449373320", "DDIA");
        pending("9781449373320", "DDIA");
        pending("9781449373320", "DDIA");
        pending("9780134757599", "Refactoring");
        SuggestionAcquisition aprobada = pending("9780132350884", "Clean Code");
        aprobada.setStatus(SuggestionAcquisition.APROBADA);
        repo.save(aprobada);
        SuggestionAcquisition withoutIsbn = pending(null, "Sin ISBN");
        withoutIsbn.setIsbn(null);
        repo.save(withoutIsbn);

        Page<SuggestionGroupedDTO> page =
                repo.findMostPedidosAgrupados(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).isbn()).isEqualTo("9781449373320");
        assertThat(page.getContent().get(0).quantity()).isEqualTo(3L);
        assertThat(page.getContent().get(1).isbn()).isEqualTo("9780134757599");
        assertThat(page.getContent().get(1).quantity()).isEqualTo(1L);
    }

    @Test
    void findMostPedidosAgrupados_pageRespetaPagination() {
        pending("9781449373320", "DDIA");
        pending("9780134757599", "Refactoring");

        Page<SuggestionGroupedDTO> page =
                repo.findMostPedidosAgrupados(PageRequest.of(1, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }
}

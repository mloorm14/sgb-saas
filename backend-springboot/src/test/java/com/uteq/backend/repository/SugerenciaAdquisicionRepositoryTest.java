package com.uteq.backend.repository;

import com.uteq.backend.dto.SugerenciaAgrupadaDTO;
import com.uteq.backend.entity.SugerenciaAdquisicion;
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
class SugerenciaAdquisicionRepositoryTest {

    @Autowired
    private SugerenciaAdquisicionRepository repo;

    private SugerenciaAdquisicion pendiente(String isbn, String titulo) {
        SugerenciaAdquisicion s = new SugerenciaAdquisicion();
        s.setUsuarioId(1L);
        s.setTitulo(titulo);
        s.setAutor("Autor");
        s.setIsbn(isbn);
        s.setEstado(SugerenciaAdquisicion.PENDIENTE);
        return repo.save(s);
    }

    @Test
    void findMasPedidosAgrupados_agrupaPorIsbnOrdenadoPorCantidad() {
        pendiente("9781449373320", "DDIA");
        pendiente("9781449373320", "DDIA");
        pendiente("9781449373320", "DDIA");
        pendiente("9780134757599", "Refactoring");
        SugerenciaAdquisicion aprobada = pendiente("9780132350884", "Clean Code");
        aprobada.setEstado(SugerenciaAdquisicion.APROBADA);
        repo.save(aprobada);
        SugerenciaAdquisicion sinIsbn = pendiente(null, "Sin ISBN");
        sinIsbn.setIsbn(null);
        repo.save(sinIsbn);

        Page<SugerenciaAgrupadaDTO> pagina =
                repo.findMasPedidosAgrupados(PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent().get(0).isbn()).isEqualTo("9781449373320");
        assertThat(pagina.getContent().get(0).cantidad()).isEqualTo(3L);
        assertThat(pagina.getContent().get(1).isbn()).isEqualTo("9780134757599");
        assertThat(pagina.getContent().get(1).cantidad()).isEqualTo(1L);
    }

    @Test
    void findMasPedidosAgrupados_paginaRespetaPaginacion() {
        pendiente("9781449373320", "DDIA");
        pendiente("9780134757599", "Refactoring");

        Page<SugerenciaAgrupadaDTO> pagina =
                repo.findMasPedidosAgrupados(PageRequest.of(1, 1));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
        assertThat(pagina.getContent()).hasSize(1);
    }
}

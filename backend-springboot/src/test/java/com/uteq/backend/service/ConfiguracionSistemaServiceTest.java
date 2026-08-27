package com.uteq.backend.service;

import com.uteq.backend.dto.ConfiguracionSistemaResponseDTO;
import com.uteq.backend.entity.ConfiguracionSistema;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.ConfiguracionSistemaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfiguracionSistemaServiceTest {

    @Mock ConfiguracionSistemaRepository repo;
    @Mock BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    @InjectMocks ConfiguracionSistemaService service;

    private ConfiguracionSistema config(String clave, String valor) {
        ConfiguracionSistema c = new ConfiguracionSistema();
        c.setClave(clave);
        c.setValor(valor);
        return c;
    }

    // ── Test 1: listar retorna todas las claves ───────────
    @Test
    void listar_retornaTodasLasClaves() {
        given(repo.findAll()).willReturn(List.of(
                config("dias_prestamo_default", "15"),
                config("max_renovaciones_default", "2")
        ));

        List<ConfiguracionSistemaResponseDTO> resultado = service.listar();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).clave()).isEqualTo("dias_prestamo_default");
    }

    // ── Test 2: actualizar clave existente ─────────────────
    @Test
    void actualizar_conClaveExistente_persisteYRetornaNuevoValor() {
        ConfiguracionSistema existente = config("dias_prestamo_default", "15");
        given(repo.findById("dias_prestamo_default")).willReturn(Optional.of(existente));
        given(repo.save(existente)).willReturn(existente);

        ConfiguracionSistemaResponseDTO resultado = service.actualizar("dias_prestamo_default", "20");

        assertThat(resultado.valor()).isEqualTo("20");
        verify(repo, times(1)).save(existente);
    }

    // ── Test 3: actualizar clave inexistente lanza excepcion ──
    @Test
    void actualizar_conClaveInexistente_lanzaExcepcion() {
        given(repo.findById("clave_fantasma")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar("clave_fantasma", "x"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("clave_fantasma");
    }

    // ── Test 4: segunda lectura viene del cache, no del repo ──
    @Test
    void obtenerValor_segundaLectura_usaCacheSinConsultarRepo() {
        given(repo.findById("max_renovaciones_default"))
                .willReturn(Optional.of(config("max_renovaciones_default", "2")));

        String primero = service.obtenerValor("max_renovaciones_default");
        String segundo = service.obtenerValor("max_renovaciones_default");

        assertThat(primero).isEqualTo("2");
        assertThat(segundo).isEqualTo("2");
        verify(repo, times(1)).findById("max_renovaciones_default");
    }

    // ── Test 5: actualizar invalida el cache previo ────────
    @Test
    void actualizar_invalidaElCachePrevio() {
        ConfiguracionSistema config = config("dias_prestamo_default", "15");
        given(repo.findById("dias_prestamo_default")).willReturn(Optional.of(config));
        given(repo.save(config)).willReturn(config);

        service.obtenerValor("dias_prestamo_default");      // cachea "15"
        service.actualizar("dias_prestamo_default", "20");  // muta y evict cache
        String resultado = service.obtenerValor("dias_prestamo_default"); // relee (cache miss)

        assertThat(resultado).isEqualTo("20");
        // 3 llamadas a findById: la lectura inicial, la de actualizar() y la relectura post-evict.
        verify(repo, times(3)).findById("dias_prestamo_default");
    }

    // ── Test 6: valor no numerico lanza excepcion ──────────
    @Test
    void obtenerValorEntero_conValorNoNumerico_lanzaExcepcion() {
        given(repo.findById("clave_texto")).willReturn(Optional.of(config("clave_texto", "no-es-numero")));

        assertThatThrownBy(() -> service.obtenerValorEntero("clave_texto"))
                .isInstanceOf(IllegalStateException.class);
    }
}

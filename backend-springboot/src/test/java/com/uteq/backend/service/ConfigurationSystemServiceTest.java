package com.uteq.backend.service;

import com.uteq.backend.dto.ConfigurationSystemResponseDTO;
import com.uteq.backend.entity.ConfigurationSystem;
import com.uteq.backend.repository.AuditLogAuditRepository;
import com.uteq.backend.repository.ConfigurationSystemRepository;
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
class ConfigurationSystemServiceTest {

    @Mock ConfigurationSystemRepository repo;
    @Mock AuditLogAuditRepository auditLogAuditRepo;

    @InjectMocks ConfigurationSystemService service;

    private ConfigurationSystem config(String key, String value) {
        ConfigurationSystem c = new ConfigurationSystem();
        c.setKey(key);
        c.setValue(value);
        return c;
    }

    // ── Test 1: listar retorna todas las claves ───────────
    @Test
    void list_retornaTodasClaves() {
        given(repo.findAll()).willReturn(List.of(
                config("dias_prestamo_default", "15"),
                config("max_renovaciones_default", "2")
        ));

        List<ConfigurationSystemResponseDTO> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).key()).isEqualTo("dias_prestamo_default");
    }

    // ── Test 2: actualizar clave existente ─────────────────
    @Test
    void update_withKeyExisting_persisteYRetornaFreshValue() {
        ConfigurationSystem existing = config("dias_prestamo_default", "15");
        given(repo.findById("dias_prestamo_default")).willReturn(Optional.of(existing));
        given(repo.save(existing)).willReturn(existing);

        ConfigurationSystemResponseDTO result = service.update("dias_prestamo_default", "20");

        assertThat(result.value()).isEqualTo("20");
        verify(repo, times(1)).save(existing);
    }

    // ── Test 3: actualizar clave inexistente lanza excepcion ──
    @Test
    void update_withKeyInexistente_lanzaException() {
        given(repo.findById("clave_fantasma")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("clave_fantasma", "x"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("clave_fantasma");
    }

    // ── Test 4: segunda lectura viene del cache, no del repo ──
    @Test
    void getValue_segundaLectura_usaCacheWithoutQueryRepo() {
        given(repo.findById("max_renovaciones_default"))
                .willReturn(Optional.of(config("max_renovaciones_default", "2")));

        String primero = service.getValue("max_renovaciones_default");
        String second = service.getValue("max_renovaciones_default");

        assertThat(primero).isEqualTo("2");
        assertThat(second).isEqualTo("2");
        verify(repo, times(1)).findById("max_renovaciones_default");
    }

    // ── Test 5: actualizar invalida el cache previo ────────
    @Test
    void update_invalidaCachePrevio() {
        ConfigurationSystem config = config("dias_prestamo_default", "15");
        given(repo.findById("dias_prestamo_default")).willReturn(Optional.of(config));
        given(repo.save(config)).willReturn(config);

        service.getValue("dias_prestamo_default");      // cachea "15"
        service.update("dias_prestamo_default", "20");  // muta y evict cache
        String result = service.getValue("dias_prestamo_default"); // relee (cache miss)

        assertThat(result).isEqualTo("20");
        // 3 llamadas a findById: la lectura inicial, la de actualizar() y la relectura post-evict.
        verify(repo, times(3)).findById("dias_prestamo_default");
    }

    // ── Test 6: valor no numerico lanza excepcion ──────────
    @Test
    void getValueEntero_withValueNotNumerico_lanzaException() {
        given(repo.findById("clave_texto")).willReturn(Optional.of(config("clave_texto", "no-es-numero")));

        assertThatThrownBy(() -> service.getValueEntero("clave_texto"))
                .isInstanceOf(IllegalStateException.class);
    }
}

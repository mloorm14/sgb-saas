package com.uteq.backend.service;

import com.uteq.backend.dto.DevolucionCompletaResponseDTO;
import com.uteq.backend.dto.DevolucionHistorialDTO;
import com.uteq.backend.dto.DevolucionRequestDTO;
import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoMulta;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.RegistroDano;
import com.uteq.backend.entity.TipoDano;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EvidenciaDanoRepository;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.RegistroDanoDetalleRepository;
import com.uteq.backend.repository.RegistroDanoRepository;
import com.uteq.backend.repository.TipoDanoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {

    @Mock PrestamoRepository prestamoRepo;
    @Mock PrestamoProcedureRepository prestamoProcRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock LibroRepository libroRepo;
    @Mock EstadoPrestamoRepository estadoPrestamoRepo;
    @Mock EstadoMultaRepository estadoMultaRepo;
    @Mock MultaRepository multaRepo;
    @Mock TipoDanoRepository tipoDanoRepo;
    @Mock RegistroDanoRepository registroDanoRepo;
    @Mock RegistroDanoDetalleRepository registroDanoDetalleRepo;
    @Mock EvidenciaDanoRepository evidenciaDanoRepo;
    @Mock BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    @InjectMocks DevolucionService devolucionService;

    // ── Test 1: devolución SIN atraso ni daño ───────────
    @Test
    void registrarDevolucion_sinAtrasoSinDano_noGeneraMulta() {
        Long prestamoId = 1L;
        Long bibliotecarioId = 10L;

        Prestamo prestamo = prestamoConId(prestamoId);
        prestamo.setFechaDevolucionReal(null);

        Map<String, Object> spResult = new HashMap<>();
        spResult.put("o_hubo_multa", false);
        spResult.put("o_monto_multa", null);

        given(prestamoRepo.findById(prestamoId)).willReturn(Optional.of(prestamo));
        given(prestamoProcRepo.spRegistrarDevolucion(prestamoId)).willReturn(spResult);

        DevolucionRequestDTO dto = new DevolucionRequestDTO(
                "BUEN_ESTADO", null, null);

        DevolucionCompletaResponseDTO resultado =
                devolucionService.registrarDevolucion(prestamoId, dto, bibliotecarioId);

        assertThat(resultado.prestamoId()).isEqualTo(prestamoId);
        assertThat(resultado.huboMultaAtraso()).isFalse();
        assertThat(resultado.huboMultaDano()).isFalse();
        assertThat(resultado.montoTotal()).isZero();
    }

    // ── Test 2: devolución CON daño ──────────────────────
    @Test
    void registrarDevolucion_conDano_generaMultaDano() {
        Long prestamoId = 2L;
        Long bibliotecarioId = 10L;

        Prestamo prestamo = prestamoConId(prestamoId);
        prestamo.setFechaDevolucionReal(null);

        Map<String, Object> spResult = new HashMap<>();
        spResult.put("o_hubo_multa", false);
        spResult.put("o_monto_multa", null);

        EstadoMulta estadoMulta = new EstadoMulta();
        estadoMulta.setId(1);

        given(prestamoRepo.findById(prestamoId)).willReturn(Optional.of(prestamo));
        given(prestamoProcRepo.spRegistrarDevolucion(prestamoId)).willReturn(spResult);
        given(estadoMultaRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoMulta));
        given(registroDanoRepo.save(any())).willAnswer(inv -> {
            RegistroDano rd = inv.getArgument(0);
            rd.setId(100L);
            return rd;
        });

        DevolucionRequestDTO.DanoItemDTO danoItem = new DevolucionRequestDTO.DanoItemDTO(
                1, null, new BigDecimal("5.00"));

        DevolucionRequestDTO dto = new DevolucionRequestDTO(
                "CON_DANO", "Pagina rasgada", List.of(danoItem));

        DevolucionCompletaResponseDTO resultado =
                devolucionService.registrarDevolucion(prestamoId, dto, bibliotecarioId);

        assertThat(resultado.huboMultaDano()).isTrue();
        assertThat(resultado.montoMultaDano()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(resultado.montoTotal()).isEqualByComparingTo(new BigDecimal("5.00"));
        verify(multaRepo).save(any(Multa.class));
    }

    // ── Test 3: devolución con préstamo YA devuelto ──────
    @Test
    void registrarDevolucion_prestamoYaDevuelto_lanzaExcepcion() {
        Long prestamoId = 3L;
        Prestamo prestamo = prestamoConId(prestamoId);
        prestamo.setFechaDevolucionReal(OffsetDateTime.now());

        given(prestamoRepo.findById(prestamoId)).willReturn(Optional.of(prestamo));

        DevolucionRequestDTO dto = new DevolucionRequestDTO(
                "BUEN_ESTADO", null, null);

        assertThatThrownBy(() -> devolucionService.registrarDevolucion(prestamoId, dto, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue devuelto");
    }

    // ── Test 4: préstamo no encontrado ──────────────────
    @Test
    void registrarDevolucion_prestamoNoEncontrado_lanzaEntityNotFound() {
        given(prestamoRepo.findById(999L)).willReturn(Optional.empty());

        DevolucionRequestDTO dto = new DevolucionRequestDTO(
                "BUEN_ESTADO", null, null);

        assertThatThrownBy(() -> devolucionService.registrarDevolucion(999L, dto, 10L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── Test 5: listar tipos de daño ────────────────────
    @Test
    void listarTiposDano_devuelveSoloActivos() {
        TipoDano activo = new TipoDano();
        activo.setId(1);
        activo.setNombre("Rasgado");
        activo.setPrecio(new BigDecimal("5.00"));
        activo.setActivo(true);

        TipoDano inactivo = new TipoDano();
        inactivo.setId(2);
        inactivo.setNombre("Manchado");
        inactivo.setPrecio(new BigDecimal("3.00"));
        inactivo.setActivo(false);

        given(tipoDanoRepo.findByActivoTrue()).willReturn(List.of(activo));

        List<TipoDanoDTO> resultado = devolucionService.listarTiposDano();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nombre()).isEqualTo("Rasgado");
        assertThat(resultado.get(0).precio()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    // ── Test 6: devolución LIBRO PERDIDO ─────────────────
    @Test
    void registrarDevolucion_libroPerdido_generaMultaValorLibro() {
        Long prestamoId = 4L;
        Long bibliotecarioId = 10L;

        Prestamo prestamo = prestamoConId(prestamoId);
        prestamo.setFechaDevolucionReal(null);
        prestamo.setLibroId(42L);

        Map<String, Object> spResult = new HashMap<>();
        spResult.put("o_hubo_multa", false);
        spResult.put("o_monto_multa", null);

        EstadoMulta estadoMulta = new EstadoMulta();
        estadoMulta.setId(1);

        Libro libro = new Libro();
        libro.setId(42L);

        given(prestamoRepo.findById(prestamoId)).willReturn(Optional.of(prestamo));
        given(prestamoProcRepo.spRegistrarDevolucion(prestamoId)).willReturn(spResult);
        given(estadoMultaRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoMulta));
        given(libroRepo.findById(42L)).willReturn(Optional.of(libro));
        given(registroDanoRepo.save(any())).willAnswer(inv -> {
            RegistroDano rd = inv.getArgument(0);
            rd.setId(200L);
            return rd;
        });

        DevolucionRequestDTO dto = new DevolucionRequestDTO(
                "PERDIDO", "Se perdio el libro", null);

        DevolucionCompletaResponseDTO resultado =
                devolucionService.registrarDevolucion(prestamoId, dto, bibliotecarioId);

        assertThat(resultado.huboMultaDano()).isTrue();
        assertThat(resultado.montoMultaDano()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(resultado.danosRegistrados()).hasSize(1);
        assertThat(resultado.danosRegistrados().get(0).tipoDanoNombre()).isEqualTo("Libro perdido");
    }

    // ── helpers ─────────────────────────────────────────

    private Prestamo prestamoConId(Long id) {
        Prestamo p = new Prestamo();
        p.setId(id);
        p.setUsuarioId(1L);
        p.setLibroId(1L);
        p.setFechaPrestamo(OffsetDateTime.now().minusDays(5));
        p.setFechaDevolucionEstimada(OffsetDateTime.now().plusDays(2));
        p.setFechaDevolucionReal(null);
        return p;
    }
}

package com.uteq.backend.service;

import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaProcedureRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.ResumenFinancieroMultasProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uteq.backend.entity.BitacoraAuditoria;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MultaServiceTest {

    @Mock MultaRepository multaRepo;
    @Mock MultaProcedureRepository multaProcRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock LibroRepository libroRepo;
    @Mock PrestamoRepository prestamoRepo;
    @Mock BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    @InjectMocks MultaService multaService;

    // ── Test 1: pagar multa invoca el SP y traduce el Map ────
    @Test
    void pagar_invocaProcedimientoYRetornaDTO() {
        Map<String, Object> mapaResultado = new HashMap<>();
        mapaResultado.put("o_multa_id", 7L);
        mapaResultado.put("o_usuario_desbloqueado", true);
        given(multaProcRepo.spPagarMulta(7L)).willReturn(mapaResultado);

        MultaAccionResponseDTO resultado = multaService.pagar(7L);

        assertThat(resultado.multaId()).isEqualTo(7L);
        assertThat(resultado.usuarioDesbloqueado()).isTrue();
    }

    // ── Test 2: pagar multa que NO desbloquea al usuario (tiene otras pendientes) ──
    @Test
    void pagar_conOtrasMultasPendientes_noDesbloqueaUsuario() {
        Map<String, Object> mapaResultado = new HashMap<>();
        mapaResultado.put("o_multa_id", 8L);
        mapaResultado.put("o_usuario_desbloqueado", false);
        given(multaProcRepo.spPagarMulta(8L)).willReturn(mapaResultado);

        MultaAccionResponseDTO resultado = multaService.pagar(8L);

        assertThat(resultado.usuarioDesbloqueado()).isFalse();
    }

    // ── Test 3: anular con rol GERENTE -> resuelve rolEjecutor del token, no del body ──
    @Test
    void anular_conRolGerente_resuelveRolDesdeAuthentication() {
        Authentication auth = authComoRol("gerente@correo.com", "GERENTE");
        given(usuarioRepo.findByCorreo("gerente@correo.com"))
                .willReturn(Optional.of(usuarioConId(10L)));
        Map<String, Object> mapaResultado = new HashMap<>();
        mapaResultado.put("o_multa_id", 9L);
        mapaResultado.put("o_usuario_desbloqueado", true);
        given(multaProcRepo.spAnularMulta(9L, "Error administrativo", "GERENTE"))
                .willReturn(mapaResultado);

        MultaAccionResponseDTO resultado = multaService.anular(9L, "Error administrativo", auth);

        assertThat(resultado.multaId()).isEqualTo(9L);
        // Verifica explícitamente que el rol enviado al SP es el de la
        // authority real ("GERENTE"), no un valor que hubiera podido venir
        // de un campo del body (que ni siquiera existe en el DTO).
        verify(multaProcRepo).spAnularMulta(9L, "Error administrativo", "GERENTE");
    }

    // ── Test 4: anular con rol ADMIN también es válido ────────
    @Test
    void anular_conRolAdmin_resuelveRolAdmin() {
        Authentication auth = authComoRol("admin@correo.com", "ADMIN");
        given(usuarioRepo.findByCorreo("admin@correo.com"))
                .willReturn(Optional.of(usuarioConId(11L)));
        Map<String, Object> mapaResultado = new HashMap<>();
        mapaResultado.put("o_multa_id", 12L);
        mapaResultado.put("o_usuario_desbloqueado", false);
        given(multaProcRepo.spAnularMulta(12L, "Duplicado", "ADMIN"))
                .willReturn(mapaResultado);

        multaService.anular(12L, "Duplicado", auth);

        verify(multaProcRepo).spAnularMulta(12L, "Duplicado", "ADMIN");
    }

    // ── Test 5: anular registra evento de auditoría con usuarioId real ──
    @Test
    void anular_deberiaRegistrarAuditoria() {
        Authentication auth = authComoRol("gerente@correo.com", "GERENTE");
        given(usuarioRepo.findByCorreo("gerente@correo.com"))
                .willReturn(Optional.of(usuarioConId(10L)));
        Map<String, Object> mapaResultado = new HashMap<>();
        mapaResultado.put("o_multa_id", 9L);
        mapaResultado.put("o_usuario_desbloqueado", false);
        given(multaProcRepo.spAnularMulta(9L, "Daño grave", "GERENTE"))
                .willReturn(mapaResultado);

        multaService.anular(9L, "Daño grave", auth);

        ArgumentCaptor<BitacoraAuditoria> captor = ArgumentCaptor.forClass(BitacoraAuditoria.class);
        verify(bitacoraAuditoriaRepo).save(captor.capture());
        BitacoraAuditoria evento = captor.getValue();
        assertThat(evento.getTablaAfectada()).isEqualTo("multas");
        assertThat(evento.getUsuarioId()).isEqualTo(10L);
        assertThat(evento.getDetalles()).isEqualTo("Anulación de la multa 9: Daño grave");
    }

    // ── Test 6: anular sin rol válido -> defensa en profundidad, denegado ──
    // (Escenario que en teoría @PreAuthorize del controller ya bloquea,
    // pero el service lo revalida por su cuenta -- ver Javadoc de
    // MultaService.resolverRolAnulacion.)
    @Test
    void anular_sinRolGerenteOAdmin_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("biblio@correo.com", "BIBLIOTECARIO");

        assertThatThrownBy(() -> multaService.anular(9L, "motivo", auth))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 6: acceso denegado cuando un LECTOR pide multas de otro usuario ──
    @Test
    void listarPorUsuario_cuandoLectorPideOtroUsuario_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("lector@correo.com", "LECTOR");
        given(usuarioRepo.findByCorreo("lector@correo.com"))
                .willReturn(Optional.of(usuarioConId(1L)));

        assertThatThrownBy(() -> multaService.listarPorUsuario(2L, auth, null))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 7: resumen financiero mapea la proyección a DTO ──
    @Test
    void reporteResumenFinanciero_conDatos_mapeaProjectionADTO() {
        ResumenFinancieroMultasProjection fila = mock(ResumenFinancieroMultasProjection.class);
        given(fila.getTotalRecaudado()).willReturn(new BigDecimal("125.00"));
        given(fila.getTotalPendiente()).willReturn(new BigDecimal("40.50"));

        ResumenFinancieroMultasProjection hoy = mock(ResumenFinancieroMultasProjection.class);
        given(hoy.getTotalRecaudado()).willReturn(new BigDecimal("10.00"));
        given(hoy.getTotalPendiente()).willReturn(new BigDecimal("5.00"));
        lenient().when(multaProcRepo.fnReporteResumenFinanciero(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(hoy);
        lenient().when(multaProcRepo.fnPagosRecientes(anyInt())).thenReturn(List.of());

        OffsetDateTime desde = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        OffsetDateTime hasta = OffsetDateTime.parse("2026-08-31T23:59:59Z");
        given(multaProcRepo.fnReporteResumenFinanciero(desde, hasta)).willReturn(fila);

        ResumenFinancieroMultasResponseDTO resultado = multaService.reporteResumenFinanciero(desde, hasta);

        assertThat(resultado.totalRecaudado()).isEqualTo(new BigDecimal("125.00"));
        assertThat(resultado.totalPendiente()).isEqualTo(new BigDecimal("40.50"));
    }

    // ── Test 8: resumen financiero sin rango de fechas envía null tal cual ──
    @Test
    void reporteResumenFinanciero_sinRangoDeFechas_envianullAlRepositorio() {
        ResumenFinancieroMultasProjection fila = mock(ResumenFinancieroMultasProjection.class);
        given(fila.getTotalRecaudado()).willReturn(BigDecimal.ZERO);
        given(fila.getTotalPendiente()).willReturn(BigDecimal.ZERO);
        given(multaProcRepo.fnReporteResumenFinanciero(null, null)).willReturn(fila);

        ResumenFinancieroMultasProjection hoy = mock(ResumenFinancieroMultasProjection.class);
        given(hoy.getTotalRecaudado()).willReturn(BigDecimal.ZERO);
        given(hoy.getTotalPendiente()).willReturn(BigDecimal.ZERO);
        lenient().when(multaProcRepo.fnReporteResumenFinanciero(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(hoy);
        lenient().when(multaProcRepo.fnPagosRecientes(anyInt())).thenReturn(List.of());

        multaService.reporteResumenFinanciero(null, null);

        verify(multaProcRepo).fnReporteResumenFinanciero(null, null);
    }

    // ── Helpers ────────────────────────────────────────────
    private Authentication authComoRol(String correo, String rol) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(correo);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + rol)))
                .when(auth).getAuthorities();
        return auth;
    }

    private Usuario usuarioConId(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }
}
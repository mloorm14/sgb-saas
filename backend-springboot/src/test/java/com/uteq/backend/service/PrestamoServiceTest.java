package com.uteq.backend.service;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.dto.RenovacionResponseDTO;
import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import com.uteq.backend.dto.ReporteUsoPorPeriodoResponseDTO;
import com.uteq.backend.entity.EstadoPrestamo;
import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.ReporteMorosidadProjection;
import com.uteq.backend.repository.projection.ReporteUsoPorPeriodoProjection;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock PrestamoRepository prestamoRepo;
    @Mock PrestamoProcedureRepository prestamoProcRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock EstadoPrestamoRepository estadoPrestamoRepo;
    @Mock ReservacionRepository reservacionRepo;
    @Mock EstadoReservacionRepository estadoReservacionRepo;
    @Mock ConfiguracionSistemaService configuracionSistemaService;
    @Mock CredencialQrService credencialQrService;
    @Mock NotificacionService notificacionService;

    @InjectMocks PrestamoService prestamoService;

    // ── Test 1: creación exitosa (usuarioId directo) ───────
    @Test
    void crear_conDatosValidos_invocaProcedimientoYRetornaDTO() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(usuarioRepo.findByCorreo("biblio@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(5L)));
        given(prestamoProcRepo.spCrearPrestamo(1L, 2L, 5L, 7)).willReturn(99L);
        given(prestamoRepo.findById(99L)).willReturn(Optional.of(prestamoConId(99L)));

        PrestamoResponseDTO resultado = prestamoService.crear(
                new PrestamoRequestDTO(1L, null, 2L, 7), auth);

        assertThat(resultado.id()).isEqualTo(99L);
        assertThat(resultado.usuarioId()).isEqualTo(1L);
    }

    // ── Test 2: devolución sin atraso (sin multa) ─────────
    @Test
    void registrarDevolucion_sinAtraso_noGeneraMulta() {
        // Map.of(...) NO admite valores null -- se usa HashMap porque
        // o_monto_multa es null cuando no hubo atraso.
        Map<String, Object> mapaSinMulta = new HashMap<>();
        mapaSinMulta.put("o_prestamo_id", 10L);
        mapaSinMulta.put("o_hubo_multa", false);
        mapaSinMulta.put("o_monto_multa", null);
        given(prestamoProcRepo.spRegistrarDevolucion(10L)).willReturn(mapaSinMulta);

        DevolucionResponseDTO resultado = prestamoService.registrarDevolucion(10L);

        assertThat(resultado.prestamoId()).isEqualTo(10L);
        assertThat(resultado.huboMulta()).isFalse();
        assertThat(resultado.montoMulta()).isNull();
    }

    // ── Test 3: devolución con atraso (hubaMulta = true) ──
    @Test
    void registrarDevolucion_conAtraso_generaMulta() {
        given(prestamoProcRepo.spRegistrarDevolucion(11L)).willReturn(Map.of(
                "o_prestamo_id", 11L,
                "o_hubo_multa", true,
                "o_monto_multa", new BigDecimal("2.50")
        ));
        given(prestamoRepo.findById(11L)).willReturn(Optional.of(prestamoConId(11L)));

        DevolucionResponseDTO resultado = prestamoService.registrarDevolucion(11L);

        assertThat(resultado.huboMulta()).isTrue();
        assertThat(resultado.montoMulta()).isEqualTo(new BigDecimal("2.50"));
        // Módulo 2: el dueño real del préstamo (usuarioId=1L, ver
        // prestamoConId) es a quien se le debe notificar, no un id
        // cualquiera.
        verify(notificacionService).notificarMulta(1L, 11L, new BigDecimal("2.50"));
    }

    // Sin atraso no hay multa que notificar -- no debe ni consultarse el
    // préstamo para esto.
    @Test
    void registrarDevolucion_sinAtraso_noNotificaMulta() {
        Map<String, Object> mapaSinMulta = new HashMap<>();
        mapaSinMulta.put("o_prestamo_id", 10L);
        mapaSinMulta.put("o_hubo_multa", false);
        mapaSinMulta.put("o_monto_multa", null);
        given(prestamoProcRepo.spRegistrarDevolucion(10L)).willReturn(mapaSinMulta);

        prestamoService.registrarDevolucion(10L);

        verify(notificacionService, org.mockito.Mockito.never())
                .notificarMulta(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ── Test 4: acceso denegado cuando un LECTOR pide el id de otro usuario ──
    @Test
    void listarPorUsuario_cuandoLectorPideOtroUsuario_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("lector@uteq.edu.ec", "LECTOR");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(1L)));

        assertThatThrownBy(() -> prestamoService.listarPorUsuario(2L, auth, null))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 5: reporte aplica default de limite=10 (regresión del fix LIMIT NULL) ──
    @Test
    void reporteLibrosMasPrestados_sinLimite_aplicaDefaultDiez() {
        given(prestamoProcRepo.fnReporteLibrosMasPrestados(10, null, null))
                .willReturn(List.of());

        prestamoService.reporteLibrosMasPrestados(null, null, null);

        // Verifica el fix del bug de LIMIT NULL: al no mandar limite,
        // el service debe pasar 10 (no null) al repositorio.
        verify(prestamoProcRepo).fnReporteLibrosMasPrestados(10, null, null);
    }

    // ── Test 6: renovación exitosa ─────────────────────────
    @Test
    void renovar_prestamoVigenteSinRenovacionesPrevias_extiendeFecha() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        Prestamo prestamo = prestamoConId(50L);
        prestamo.setRenovacionesRealizadas((short) 0);
        prestamo.setEstadoPrestamoId(1);
        given(prestamoRepo.findById(50L)).willReturn(Optional.of(prestamo));
        given(estadoPrestamoRepo.findById(1)).willReturn(Optional.of(estadoPrestamo(1, "ACTIVO")));
        given(configuracionSistemaService.obtenerValorEntero("max_renovaciones_default")).willReturn(2);
        given(estadoReservacionRepo.findByNombre("PENDIENTE"))
                .willReturn(Optional.of(estadoReservacion(1, "PENDIENTE")));
        given(estadoReservacionRepo.findByNombre("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(estadoReservacion(2, "LISTA_PARA_RETIRO")));
        given(reservacionRepo.existsByLibroIdAndEstadoReservacionIdInAndUsuarioIdNot(2L, List.of(1, 2), 1L))
                .willReturn(false);
        given(configuracionSistemaService.obtenerValorEntero("dias_prestamo_default")).willReturn(15);
        given(estadoPrestamoRepo.findByNombre("RENOVADO")).willReturn(Optional.of(estadoPrestamo(2, "RENOVADO")));

        RenovacionResponseDTO resultado = prestamoService.renovar(50L, auth);

        assertThat(resultado.renovacionesRealizadas()).isEqualTo((short) 1);
        assertThat(resultado.renovacionesRestantes()).isEqualTo((short) 1);
        assertThat(prestamo.getEstadoPrestamoId()).isEqualTo(2);
        verify(prestamoRepo).save(prestamo);
    }

    // ── Test 7: préstamo vencido ────────────────────────────
    @Test
    void renovar_prestamoVencido_lanzaExcepcion() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        Prestamo prestamo = prestamoConId(51L);
        prestamo.setEstadoPrestamoId(1);
        prestamo.setFechaDevolucionEstimada(OffsetDateTime.now().minusDays(2));
        given(prestamoRepo.findById(51L)).willReturn(Optional.of(prestamo));
        given(estadoPrestamoRepo.findById(1)).willReturn(Optional.of(estadoPrestamo(1, "ACTIVO")));

        assertThatThrownBy(() -> prestamoService.renovar(51L, auth))
                .isInstanceOf(PrestamoVencidoException.class);
    }

    // ── Test 8: límite de renovaciones alcanzado ───────────
    @Test
    void renovar_limiteRenovacionesAlcanzado_lanzaExcepcion() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        Prestamo prestamo = prestamoConId(52L);
        prestamo.setEstadoPrestamoId(1);
        prestamo.setRenovacionesRealizadas((short) 2);
        given(prestamoRepo.findById(52L)).willReturn(Optional.of(prestamo));
        given(estadoPrestamoRepo.findById(1)).willReturn(Optional.of(estadoPrestamo(1, "ACTIVO")));
        given(configuracionSistemaService.obtenerValorEntero("max_renovaciones_default")).willReturn(2);

        assertThatThrownBy(() -> prestamoService.renovar(52L, auth))
                .isInstanceOf(LimiteRenovacionesExcedidoException.class);
    }

    // ── Test 9: material reservado por otro usuario ────────
    @Test
    void renovar_materialReservadoPorOtroUsuario_lanzaExcepcion() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        Prestamo prestamo = prestamoConId(53L);
        prestamo.setEstadoPrestamoId(1);
        given(prestamoRepo.findById(53L)).willReturn(Optional.of(prestamo));
        given(estadoPrestamoRepo.findById(1)).willReturn(Optional.of(estadoPrestamo(1, "ACTIVO")));
        given(configuracionSistemaService.obtenerValorEntero("max_renovaciones_default")).willReturn(2);
        given(estadoReservacionRepo.findByNombre("PENDIENTE"))
                .willReturn(Optional.of(estadoReservacion(1, "PENDIENTE")));
        given(estadoReservacionRepo.findByNombre("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(estadoReservacion(2, "LISTA_PARA_RETIRO")));
        given(reservacionRepo.existsByLibroIdAndEstadoReservacionIdInAndUsuarioIdNot(2L, List.of(1, 2), 1L))
                .willReturn(true);

        assertThatThrownBy(() -> prestamoService.renovar(53L, auth))
                .isInstanceOf(MaterialReservadoException.class);
    }

    // ── Test 10: LECTOR intenta renovar un préstamo ajeno ──
    @Test
    void renovar_lectorIntentaRenovarPrestamoAjeno_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("lector@uteq.edu.ec", "LECTOR");
        Prestamo prestamo = prestamoConId(54L); // usuarioId = 1L (ver helper)
        given(prestamoRepo.findById(54L)).willReturn(Optional.of(prestamo));
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec")).willReturn(Optional.of(usuarioConId(99L)));

        assertThatThrownBy(() -> prestamoService.renovar(54L, auth))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 11: préstamo ya devuelto ──────────────────────
    @Test
    void renovar_prestamoYaDevuelto_lanzaExcepcion() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        Prestamo prestamo = prestamoConId(55L);
        prestamo.setEstadoPrestamoId(3);
        given(prestamoRepo.findById(55L)).willReturn(Optional.of(prestamo));
        given(estadoPrestamoRepo.findById(3)).willReturn(Optional.of(estadoPrestamo(3, "DEVUELTO")));

        assertThatThrownBy(() -> prestamoService.renovar(55L, auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 12: creación con credencial QR válida ──────────
    @Test
    void crear_conCredencialQrValida_resuelveUsuarioCorrecto() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        UUID token = UUID.randomUUID();
        given(usuarioRepo.findByCorreo("biblio@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(5L)));
        given(credencialQrService.resolverPorToken(token)).willReturn(usuarioConId(3L));
        given(prestamoProcRepo.spCrearPrestamo(3L, 2L, 5L, 7)).willReturn(100L);
        given(prestamoRepo.findById(100L)).willReturn(Optional.of(prestamoConId(100L)));

        PrestamoResponseDTO resultado = prestamoService.crear(
                new PrestamoRequestDTO(null, token, 2L, 7), auth);

        assertThat(resultado.id()).isEqualTo(100L);
        verify(prestamoProcRepo).spCrearPrestamo(3L, 2L, 5L, 7);
    }

    // ── Test 13: credencial QR que no resuelve a ningún usuario ──
    @Test
    void crear_conCredencialQrInexistente_lanza404() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        UUID token = UUID.randomUUID();
        given(credencialQrService.resolverPorToken(token))
                .willThrow(new EntityNotFoundException("Credencial QR no reconocida o usuario inactivo."));

        assertThatThrownBy(() -> prestamoService.crear(
                new PrestamoRequestDTO(null, token, 2L, 7), auth))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 14: usuarioId y credencialQrToken ambos presentes ──
    @Test
    void crear_conUsuarioIdYCredencialQrAmbosPresentes_lanzaExcepcion() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");

        assertThatThrownBy(() -> prestamoService.crear(
                new PrestamoRequestDTO(1L, UUID.randomUUID(), 2L, 7), auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 15: ni usuarioId ni credencialQrToken ───────────
    @Test
    void crear_sinUsuarioIdNiCredencialQr_lanzaExcepcion() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");

        assertThatThrownBy(() -> prestamoService.crear(
                new PrestamoRequestDTO(null, null, 2L, 7), auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 16: reporte de morosidad aplica default de limite=10 ──
    // Mismo motivo/patrón que el Test 5 (reporteLibrosMasPrestados): la
    // @Query nativeQuery de PrestamoProcedureRepository siempre manda
    // p_limite explícito, así que sin este default un null produciría
    // "LIMIT NULL" (sin límite) en Postgres.
    @Test
    void reporteMorosidad_sinLimite_aplicaDefaultDiez() {
        given(prestamoProcRepo.fnReporteIndiceMorosidad(10)).willReturn(List.of());

        prestamoService.reporteMorosidad(null);

        verify(prestamoProcRepo).fnReporteIndiceMorosidad(10);
    }

    // ── Test 17: reporte de morosidad mapea la proyección a DTO ──
    @Test
    void reporteMorosidad_conFilas_mapeaProjectionADTO() {
        ReporteMorosidadProjection fila = mock(ReporteMorosidadProjection.class);
        given(fila.getUsuarioId()).willReturn(3L);
        given(fila.getNombre()).willReturn("Ana");
        given(fila.getApellido()).willReturn("Pérez");
        given(fila.getCorreo()).willReturn("ana@uteq.edu.ec");
        given(fila.getMontoTotalAdeudado()).willReturn(new BigDecimal("15.50"));
        given(fila.getCantidadMultasPendientes()).willReturn(2L);
        given(fila.getDiasAtrasoPromedio()).willReturn(new BigDecimal("3.5"));
        given(prestamoProcRepo.fnReporteIndiceMorosidad(5)).willReturn(List.of(fila));

        List<ReporteMorosidadResponseDTO> resultado = prestamoService.reporteMorosidad(5);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).usuarioId()).isEqualTo(3L);
        assertThat(resultado.get(0).montoTotalAdeudado()).isEqualTo(new BigDecimal("15.50"));
    }

    // ── Test 18: reporte de uso con granularidad válida ──────
    @Test
    void reporteUsoPorPeriodo_conGranularidadValida_invocaRepositorioConValorNormalizado() {
        ReporteUsoPorPeriodoProjection fila = mock(ReporteUsoPorPeriodoProjection.class);
        given(fila.getPeriodo()).willReturn(OffsetDateTime.now());
        given(fila.getTotalPrestamos()).willReturn(4L);
        given(fila.getTotalDevoluciones()).willReturn(2L);
        given(prestamoProcRepo.fnReporteUsoPorPeriodo("semana", null, null))
                .willReturn(List.of(fila));

        // "SEMANA" en mayúsculas para verificar que el service normaliza
        // (toLowerCase) antes de comparar contra la lista blanca y de
        // invocar al repositorio.
        List<ReporteUsoPorPeriodoResponseDTO> resultado =
                prestamoService.reporteUsoPorPeriodo("SEMANA", null, null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).totalPrestamos()).isEqualTo(4L);
        verify(prestamoProcRepo).fnReporteUsoPorPeriodo("semana", null, null);
    }

    // ── Test 19: reporte de uso con granularidad inválida ────
    @Test
    void reporteUsoPorPeriodo_conGranularidadInvalida_lanzaExcepcion() {
        assertThatThrownBy(() -> prestamoService.reporteUsoPorPeriodo("dias", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helpers ────────────────────────────────────────────
    // lenient(): MockitoExtension usa strict stubbing por defecto -- un
    // test que solo llama getName() (ej. crear()) pero no getAuthorities()
    // haría fallar con UnnecessaryStubbingException si se stubea con
    // given() normal. Como este helper se reutiliza en tests que llaman
    // subconjuntos distintos de métodos de Authentication, se marca
    // lenient a propósito.
    //
    // doReturn(...).when(...) en vez de when(...).thenReturn(...): el tipo
    // real de Authentication.getAuthorities() es
    // Collection<? extends GrantedAuthority>, y el wildcard capturado por
    // javac exige el tipo exacto para thenReturn, no un subtipo como
    // List<SimpleGrantedAuthority>. doReturn() recibe Object y evita el
    // problema de inferencia genérica por completo.
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

    private Prestamo prestamoConId(Long id) {
        Prestamo prestamo = new Prestamo();
        prestamo.setId(id);
        prestamo.setUsuarioId(1L);
        prestamo.setLibroId(2L);
        prestamo.setBibliotecarioId(5L);
        prestamo.setFechaPrestamo(OffsetDateTime.now());
        prestamo.setFechaDevolucionEstimada(OffsetDateTime.now().plusDays(7));
        prestamo.setEstadoPrestamoId(1);
        return prestamo;
    }

    private EstadoPrestamo estadoPrestamo(Integer id, String nombre) {
        EstadoPrestamo estado = new EstadoPrestamo();
        estado.setId(id);
        estado.setNombre(nombre);
        return estado;
    }

    private EstadoReservacion estadoReservacion(Integer id, String nombre) {
        EstadoReservacion estado = new EstadoReservacion();
        estado.setId(id);
        estado.setNombre(nombre);
        return estado;
    }
}
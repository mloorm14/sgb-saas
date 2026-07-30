package com.uteq.backend.service;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.UsuarioRepository;
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

    @InjectMocks PrestamoService prestamoService;

    // ── Test 1: creación exitosa ───────────────────────────
    @Test
    void crear_conDatosValidos_invocaProcedimientoYRetornaDTO() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(usuarioRepo.findByCorreo("biblio@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(5L)));
        given(prestamoProcRepo.spCrearPrestamo(1L, 2L, 5L, 7)).willReturn(99L);
        given(prestamoRepo.findById(99L)).willReturn(Optional.of(prestamoConId(99L)));

        PrestamoResponseDTO resultado = prestamoService.crear(
                new PrestamoRequestDTO(1L, 2L, 7), auth);

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

        DevolucionResponseDTO resultado = prestamoService.registrarDevolucion(11L);

        assertThat(resultado.huboMulta()).isTrue();
        assertThat(resultado.montoMulta()).isEqualTo(new BigDecimal("2.50"));
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
}
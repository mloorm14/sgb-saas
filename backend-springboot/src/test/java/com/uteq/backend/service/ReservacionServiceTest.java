package com.uteq.backend.service;

import com.uteq.backend.dto.CambioEstadoReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservacionServiceTest {

    @Mock ReservacionRepository reservacionRepo;
    @Mock EstadoReservacionRepository estadoReservacionRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    @InjectMocks ReservacionService reservacionService;

    // ── Test 1: creación exitosa (LECTOR reserva para sí mismo) ──
    @Test
    void crear_lectorReservaParaSiMismo_creaReservacionPendiente() {
        Authentication auth = authComoRol("lector@uteq.edu.ec", "LECTOR");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(1L)));
        given(estadoReservacionRepo.findByNombre("PENDIENTE"))
                .willReturn(Optional.of(estadoConId(1)));
        given(reservacionRepo.save(any())).willAnswer(inv -> {
            Reservacion r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReservacionResponseDTO resultado = reservacionService.crear(
                new ReservacionRequestDTO(1L, 3L), auth);

        assertThat(resultado.id()).isEqualTo(50L);
        assertThat(resultado.usuarioId()).isEqualTo(1L);
        assertThat(resultado.libroId()).isEqualTo(3L);
        assertThat(resultado.estadoReservacionId()).isEqualTo(1);
        assertThat(resultado.fechaLimiteRetiro()).isAfter(resultado.fechaReserva());
    }

    // ── Test 2: LECTOR intenta reservar para OTRO usuario -> denegado ──
    @Test
    void crear_lectorReservaParaOtroUsuario_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("lector@uteq.edu.ec", "LECTOR");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(1L)));

        assertThatThrownBy(() -> reservacionService.crear(
                new ReservacionRequestDTO(2L, 3L), auth))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 3: BIBLIOTECARIO reserva en nombre de otro usuario -> permitido ──
    @Test
    void crear_bibliotecarioReservaParaOtroUsuario_sePermite() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(estadoReservacionRepo.findByNombre("PENDIENTE"))
                .willReturn(Optional.of(estadoConId(1)));
        given(reservacionRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ReservacionResponseDTO resultado = reservacionService.crear(
                new ReservacionRequestDTO(2L, 3L), auth);

        assertThat(resultado.usuarioId()).isEqualTo(2L);
    }

    // ── Test 4: catálogo PENDIENTE faltante -> error de sistema (500), no 404 ──
    @Test
    void crear_sinCatalogoPendiente_lanzaIllegalState() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(estadoReservacionRepo.findByNombre("PENDIENTE"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reservacionService.crear(
                new ReservacionRequestDTO(2L, 3L), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDIENTE");
    }

    // ── Test 5: acceso denegado al listar reservaciones de otro usuario ──
    @Test
    void listarPorUsuario_cuandoLectorPideOtroUsuario_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("lector@uteq.edu.ec", "LECTOR");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConId(1L)));

        assertThatThrownBy(() -> reservacionService.listarPorUsuario(2L, auth, null))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 6: aceptar una reservación pendiente -> LISTA_PARA_RETIRO ──
    @Test
    void cambiarEstado_aceptarPendiente_quedaListaParaRetiro() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(reservacionRepo.findById(50L)).willReturn(Optional.of(reservacionPendiente(50L)));
        given(estadoReservacionRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoConId(1)));
        given(estadoReservacionRepo.findByNombre("LISTA_PARA_RETIRO")).willReturn(Optional.of(estadoConId(2)));
        given(usuarioRepo.findByCorreo("biblio@uteq.edu.ec")).willReturn(Optional.of(usuarioConId(1L)));

        ReservacionResponseDTO resultado = reservacionService.cambiarEstado(
                50L, new CambioEstadoReservacionRequestDTO("LISTA_PARA_RETIRO"), auth);

        assertThat(resultado.id()).isEqualTo(50L);
        assertThat(resultado.estadoReservacionId()).isEqualTo(2);
        verify(reservacionRepo).save(any(Reservacion.class));
        verify(bitacoraAuditoriaRepo).save(any(BitacoraAuditoria.class));
    }

    // ── Test 7: rechazar una reservación pendiente -> CANCELADA ──
    @Test
    void cambiarEstado_rechazarPendiente_quedaCancelada() {
        Authentication auth = authComoRol("gerente@uteq.edu.ec", "GERENTE");
        given(reservacionRepo.findById(51L)).willReturn(Optional.of(reservacionPendiente(51L)));
        given(estadoReservacionRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoConId(1)));
        given(estadoReservacionRepo.findByNombre("CANCELADA")).willReturn(Optional.of(estadoConId(5)));
        given(usuarioRepo.findByCorreo("gerente@uteq.edu.ec")).willReturn(Optional.of(usuarioConId(2L)));

        ReservacionResponseDTO resultado = reservacionService.cambiarEstado(
                51L, new CambioEstadoReservacionRequestDTO("CANCELADA"), auth);

        assertThat(resultado.estadoReservacionId()).isEqualTo(5);
        verify(bitacoraAuditoriaRepo).save(any(BitacoraAuditoria.class));
    }

    // ── Test 8: reservación inexistente -> 404 ──
    @Test
    void cambiarEstado_reservacionNoExiste_lanzaEntityNotFound() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(reservacionRepo.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservacionService.cambiarEstado(
                999L, new CambioEstadoReservacionRequestDTO("CANCELADA"), auth))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── Test 9: ya no está pendiente -> no se puede aceptar/rechazar ──
    @Test
    void cambiarEstado_reservacionYaRetirada_lanzaIllegalState() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        Reservacion retirada = reservacionPendiente(52L);
        retirada.setEstadoReservacionId(3);
        given(reservacionRepo.findById(52L)).willReturn(Optional.of(retirada));
        given(estadoReservacionRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoConId(1)));

        assertThatThrownBy(() -> reservacionService.cambiarEstado(
                52L, new CambioEstadoReservacionRequestDTO("LISTA_PARA_RETIRO"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pendiente");
    }

    // ── Test 10: catálogo destino faltante -> error de sistema (500) ──
    @Test
    void cambiarEstado_sinCatalogoDestino_lanzaIllegalState() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(reservacionRepo.findById(53L)).willReturn(Optional.of(reservacionPendiente(53L)));
        given(estadoReservacionRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoConId(1)));
        given(estadoReservacionRepo.findByNombre("CANCELADA")).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservacionService.cambiarEstado(
                53L, new CambioEstadoReservacionRequestDTO("CANCELADA"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELADA");
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

    private EstadoReservacion estadoConId(Integer id) {
        EstadoReservacion estado = new EstadoReservacion();
        estado.setId(id);
        return estado;
    }

    private Reservacion reservacionPendiente(Long id) {
        Reservacion r = new Reservacion();
        r.setId(id);
        r.setUsuarioId(1L);
        r.setLibroId(3L);
        r.setEstadoReservacionId(1);
        return r;
    }
}
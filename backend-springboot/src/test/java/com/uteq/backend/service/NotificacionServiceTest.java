package com.uteq.backend.service;

import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.TipoNotificacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.NotificacionRepository;
import com.uteq.backend.repository.TipoNotificacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock NotificacionRepository notificacionRepo;
    @Mock TipoNotificacionRepository tipoNotificacionRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock LibroRepository libroRepo;
    @Mock EmailService emailService;

    @InjectMocks NotificacionService notificacionService;

    // ── Test 1: alerta de vencimiento -- caso normal, se envía y se guarda ──
    @Test
    void generarAlertaVencimiento_prestamoSinAlertaPrevia_creaYEnviaNotificacion() {
        given(tipoNotificacionRepo.findByNombre("VENCIMIENTO"))
                .willReturn(Optional.of(tipoConId(1, "VENCIMIENTO")));
        given(notificacionRepo.existsByPrestamoIdAndTipoNotificacionId(50L, 1)).willReturn(false);
        given(usuarioRepo.findById(1L)).willReturn(Optional.of(usuarioConCorreo(1L, "lector@uteq.edu.ec")));
        given(libroRepo.findById(2L)).willReturn(Optional.of(libroConTitulo("Clean Code")));
        given(emailService.enviarCorreo(eq("lector@uteq.edu.ec"), anyString(), anyString())).willReturn(true);

        notificacionService.generarAlertaVencimiento(prestamoConId(50L));

        verify(notificacionRepo).save(any());
        verify(emailService).enviarCorreo(eq("lector@uteq.edu.ec"), anyString(), anyString());
    }

    // ── Test 2: dedup -- ya existe una alerta VENCIMIENTO para este préstamo ──
    @Test
    void generarAlertaVencimiento_yaNotificadoAntes_noReenviaNiDuplica() {
        given(tipoNotificacionRepo.findByNombre("VENCIMIENTO"))
                .willReturn(Optional.of(tipoConId(1, "VENCIMIENTO")));
        given(notificacionRepo.existsByPrestamoIdAndTipoNotificacionId(50L, 1)).willReturn(true);

        notificacionService.generarAlertaVencimiento(prestamoConId(50L));

        verify(notificacionRepo, never()).save(any());
        verify(emailService, never()).enviarCorreo(any(), any(), any());
    }

    // ── Test 3: fallo de SMTP no propaga, y queda registrado enviadoOk=false ──
    @Test
    void notificarMulta_fallaElEnvio_igualGuardaElRegistroConEnviadoOkFalse() {
        given(tipoNotificacionRepo.findByNombre("MULTA")).willReturn(Optional.of(tipoConId(2, "MULTA")));
        given(usuarioRepo.findById(1L)).willReturn(Optional.of(usuarioConCorreo(1L, "lector@uteq.edu.ec")));
        given(emailService.enviarCorreo(any(), any(), any())).willReturn(false);

        notificacionService.notificarMulta(1L, 50L, new BigDecimal("2.50"));

        ArgumentCaptor<com.uteq.backend.entity.Notificacion> captor =
                ArgumentCaptor.forClass(com.uteq.backend.entity.Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        assertThat(captor.getValue().isEnviadoOk()).isFalse();
        assertThat(captor.getValue().getErrorEnvio()).isNotNull();
    }

    // ── Test 4: reserva caducada -- prestamoId debe quedar null (no hay préstamo de origen) ──
    @Test
    void notificarReservaCaducada_guardaConPrestamoIdNulo() {
        given(tipoNotificacionRepo.findByNombre("RESERVA_CADUCADA"))
                .willReturn(Optional.of(tipoConId(3, "RESERVA_CADUCADA")));
        given(usuarioRepo.findById(1L)).willReturn(Optional.of(usuarioConCorreo(1L, "lector@uteq.edu.ec")));
        given(libroRepo.findById(2L)).willReturn(Optional.of(libroConTitulo("Clean Code")));
        given(emailService.enviarCorreo(any(), any(), any())).willReturn(true);

        Reservacion reservacion = new Reservacion();
        reservacion.setId(70L);
        reservacion.setUsuarioId(1L);
        reservacion.setLibroId(2L);

        notificacionService.notificarReservaCaducada(reservacion);

        ArgumentCaptor<com.uteq.backend.entity.Notificacion> captor =
                ArgumentCaptor.forClass(com.uteq.backend.entity.Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        assertThat(captor.getValue().getPrestamoId()).isNull();
    }

    // ── Test 5: un LECTOR pidiendo el listado de OTRO usuario -> 403 ──
    @Test
    void listarPorUsuario_lectorPideOtroUsuario_lanzaAccesoDenegado() {
        Authentication auth = authComoRol("lector@uteq.edu.ec", "LECTOR");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec"))
                .willReturn(Optional.of(usuarioConCorreo(1L, "lector@uteq.edu.ec")));

        assertThatThrownBy(() -> notificacionService.listarPorUsuario(99L, auth, mock(Pageable.class)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 6: un BIBLIOTECARIO puede consultar cualquier usuario ──
    @Test
    void listarPorUsuario_bibliotecarioPideCualquierUsuario_sePermite() {
        Authentication auth = authComoRol("biblio@uteq.edu.ec", "BIBLIOTECARIO");
        given(notificacionRepo.findByUsuarioId(eq(99L), any())).willReturn(Page.empty());

        Page<?> resultado = notificacionService.listarPorUsuario(99L, auth, mock(Pageable.class));

        assertThat(resultado).isEmpty();
    }

    private Authentication authComoRol(String correo, String rol) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(correo);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + rol)))
                .when(auth).getAuthorities();
        return auth;
    }

    private TipoNotificacion tipoConId(Integer id, String nombre) {
        TipoNotificacion tipo = new TipoNotificacion();
        tipo.setId(id);
        tipo.setNombre(nombre);
        return tipo;
    }

    private Usuario usuarioConCorreo(Long id, String correo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCorreo(correo);
        return usuario;
    }

    private Libro libroConTitulo(String titulo) {
        Libro libro = new Libro();
        libro.setTitulo(titulo);
        return libro;
    }

    private Prestamo prestamoConId(Long id) {
        Prestamo prestamo = new Prestamo();
        prestamo.setId(id);
        prestamo.setUsuarioId(1L);
        prestamo.setLibroId(2L);
        prestamo.setFechaDevolucionEstimada(OffsetDateTime.now().plusMinutes(10));
        return prestamo;
    }
}
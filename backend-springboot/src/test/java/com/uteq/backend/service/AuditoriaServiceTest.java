package com.uteq.backend.service;

import com.uteq.backend.dto.EventoAuditoriaResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    @Mock UsuarioRepository usuarioRepo;

    @InjectMocks AuditoriaService service;

    private Usuario usuario(Long id, String correo) {
        Instant ahora = Instant.now();
        EstadoUsuario activo = new EstadoUsuario();
        activo.setId(1);
        activo.setNombre("ACTIVO");
        Rol lector = new Rol();
        lector.setId(1);
        lector.setNombre("LECTOR");
        return Usuario.builder()
                .id(id)
                .nombre("Nombre")
                .apellido("Apellido")
                .correo(correo)
                .passwordHash("hash")
                .estado(activo)
                .correoVerificado(true)
                .roles(Set.of(lector))
                .fechaRegistro(ahora)
                .actualizadoEn(ahora)
                .build();
    }

    private BitacoraAuditoria evento(Long id, Long usuarioId, String tipoOperacion,
                                      String tablaAfectada, String detalles) {
        return BitacoraAuditoria.builder()
                .id(id)
                .usuarioId(usuarioId)
                .tipoOperacion(tipoOperacion)
                .tablaAfectada(tablaAfectada)
                .registroId(usuarioId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
    }

    @Test
    void listar_resuelveCorreoDeEventosConUsuarioIdConocido() {
        Pageable pageable = PageRequest.of(0, 20);
        BitacoraAuditoria evt = evento(1L, 9L, "UPDATE", "usuarios", "Cambio de rol");
        Page<BitacoraAuditoria> pagina = new PageImpl<>(List.of(evt), pageable, 1);

        given(bitacoraAuditoriaRepo.buscarConFiltros(null, null, null, null, pageable))
                .willReturn(pagina);
        given(usuarioRepo.findAllById(Set.of(9L))).willReturn(List.of(usuario(9L, "admin@correo.com")));

        Page<EventoAuditoriaResponseDTO> resultado = service.listar(null, null, null, null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).usuario()).isEqualTo("admin@correo.com");
        assertThat(resultado.getContent().get(0).accion()).isEqualTo("UPDATE");
        assertThat(resultado.getContent().get(0).modulo()).isEqualTo("usuarios");
    }

    // LOGIN_FAIL se registra con usuarioId null (AuthService.registrarAuditoria)
    // -- el DTO debe reflejar eso como usuario null, no romper ni inventar un
    // valor, y no debe ni siquiera consultar UsuarioRepository para ids vacíos.
    @Test
    void listar_conUsuarioIdNulo_dejaUsuarioNuloYNoConsultaUsuarios() {
        Pageable pageable = PageRequest.of(0, 20);
        BitacoraAuditoria evt = evento(2L, null, "LOGIN_FAIL", "usuarios", "Login fallido para correo: x@x.com");
        Page<BitacoraAuditoria> pagina = new PageImpl<>(List.of(evt), pageable, 1);

        given(bitacoraAuditoriaRepo.buscarConFiltros(null, null, null, null, pageable))
                .willReturn(pagina);
        given(usuarioRepo.findAllById(Set.of())).willReturn(List.of());

        Page<EventoAuditoriaResponseDTO> resultado = service.listar(null, null, null, null, pageable);

        assertThat(resultado.getContent().get(0).usuario()).isNull();
        assertThat(resultado.getContent().get(0).accion()).isEqualTo("LOGIN_FAIL");
    }

    @Test
    void listar_conFiltrosDeUsuarioModuloYFecha_lospasaTalCualAlRepositorio() {
        Pageable pageable = PageRequest.of(0, 20);
        OffsetDateTime desde = OffsetDateTime.now().minusDays(7);
        OffsetDateTime hasta = OffsetDateTime.now();

        given(bitacoraAuditoriaRepo.buscarConFiltros(eq(9L), eq("usuarios"), eq(desde), eq(hasta), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        service.listar(9L, "usuarios", desde, hasta, pageable);

        verify(bitacoraAuditoriaRepo, times(1))
                .buscarConFiltros(9L, "usuarios", desde, hasta, pageable);
    }

    @Test
    void listar_sinResultados_noConsultaUsuariosYRetornaPaginaVacia() {
        Pageable pageable = PageRequest.of(0, 20);
        given(bitacoraAuditoriaRepo.buscarConFiltros(any(), any(), any(), any(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<EventoAuditoriaResponseDTO> resultado = service.listar(null, null, null, null, pageable);

        assertThat(resultado.getContent()).isEmpty();
        verify(usuarioRepo, times(1)).findAllById(Set.of());
    }
}

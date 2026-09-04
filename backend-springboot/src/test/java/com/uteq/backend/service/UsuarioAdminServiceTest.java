package com.uteq.backend.service;

import com.uteq.backend.dto.UsuarioListadoResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EstadoMultaRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.RolRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsuarioAdminServiceTest {

    @Mock UsuarioRepository usuarioRepo;
    @Mock RolRepository rolRepo;
    @Mock EstadoUsuarioRepository estadoUsuarioRepo;
    @Mock BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    @Mock MultaRepository multaRepo;
    @Mock EstadoMultaRepository estadoMultaRepo;
    @Mock Authentication authentication;

    @InjectMocks UsuarioAdminService service;

    private EstadoUsuario estado(String nombre) {
        EstadoUsuario e = new EstadoUsuario();
        e.setId(1);
        e.setNombre(nombre);
        return e;
    }

    private Rol rol(String nombre) {
        Rol r = new Rol();
        r.setId(1);
        r.setNombre(nombre);
        return r;
    }

    private Usuario usuario(Long id, String correo, String rolNombre, String estadoNombre) {
        Instant ahora = Instant.now();
        return Usuario.builder()
                .id(id)
                .nombre("Nombre")
                .apellido("Apellido")
                .correo(correo)
                .passwordHash("hash")
                .estado(estado(estadoNombre))
                .correoVerificado(true)
                .roles(Set.of(rol(rolNombre)))
                .fechaRegistro(ahora)
                .actualizadoEn(ahora)
                .build();
    }

    // ── listar ──────────────────────────────────────────────

    @Test
    void listar_retornaUsuariosMapeadosYMarcaMultasPendientesSegunEstado() {
        Usuario bloqueado = usuario(1L, "bloqueado@correo.com", "LECTOR", "BLOQUEADO_POR_MULTA");
        Usuario activo = usuario(2L, "activo@correo.com", "LECTOR", "ACTIVO");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Usuario> pagina = new PageImpl<>(List.of(bloqueado, activo), pageable, 2);

        given(usuarioRepo.buscarConFiltros("", null, pageable))
                .willReturn(pagina);

        com.uteq.backend.entity.EstadoMulta estadoPendiente = new com.uteq.backend.entity.EstadoMulta();
        estadoPendiente.setId(1);
        estadoPendiente.setNombre("PENDIENTE");
        given(estadoMultaRepo.findByNombre("PENDIENTE")).willReturn(Optional.of(estadoPendiente));
        given(multaRepo.findUsuarioIdsConMultasPendientes(List.of(1L, 2L), 1))
                .willReturn(List.of(1L));

        Page<UsuarioListadoResponseDTO> resultado = service.listar(null, pageable);

        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.getContent().get(0).multasPendientes()).isTrue();
        assertThat(resultado.getContent().get(1).multasPendientes()).isFalse();
        assertThat(resultado.getContent().get(0).roles()).containsExactly("LECTOR");
    }

    // ── cambiarRol ──────────────────────────────────────────

    @Test
    void cambiarRol_conDatosValidos_actualizaRolesYRegistraAuditoria() {
        Usuario usuarioObjetivo = usuario(5L, "lector@correo.com", "LECTOR", "ACTIVO");
        Usuario admin = usuario(9L, "admin@correo.com", "ADMIN", "ACTIVO");

        given(usuarioRepo.findById(5L)).willReturn(Optional.of(usuarioObjetivo));
        given(rolRepo.findByNombre("BIBLIOTECARIO")).willReturn(Optional.of(rol("BIBLIOTECARIO")));
        given(authentication.getName()).willReturn("admin@correo.com");
        given(usuarioRepo.findByCorreo("admin@correo.com")).willReturn(Optional.of(admin));

        service.cambiarRol(5L, "BIBLIOTECARIO", authentication);

        assertThat(usuarioObjetivo.getRoles()).extracting(Rol::getNombre).containsExactly("BIBLIOTECARIO");
        verify(usuarioRepo, times(1)).save(usuarioObjetivo);

        ArgumentCaptor<BitacoraAuditoria> captor = ArgumentCaptor.forClass(BitacoraAuditoria.class);
        verify(bitacoraAuditoriaRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(9L);
        assertThat(captor.getValue().getRegistroId()).isEqualTo(5L);
        assertThat(captor.getValue().getDetalles()).contains("BIBLIOTECARIO");
    }

    @Test
    void cambiarRol_conUsuarioInexistente_lanzaEntityNotFound() {
        given(usuarioRepo.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarRol(404L, "ADMIN", authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void cambiarRol_conRolInexistenteEnCatalogo_lanzaIllegalArgument() {
        Usuario usuarioObjetivo = usuario(5L, "lector@correo.com", "LECTOR", "ACTIVO");
        given(usuarioRepo.findById(5L)).willReturn(Optional.of(usuarioObjetivo));
        given(rolRepo.findByNombre("SUPERVISOR")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarRol(5L, "SUPERVISOR", authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPERVISOR");
    }

    // ── cambiarEstado ───────────────────────────────────────

    @Test
    void cambiarEstado_conDatosValidos_actualizaEstadoYRegistraAuditoriaConMotivo() {
        Usuario usuarioObjetivo = usuario(5L, "lector@correo.com", "LECTOR", "ACTIVO");
        Usuario admin = usuario(9L, "admin@correo.com", "ADMIN", "ACTIVO");

        given(usuarioRepo.findById(5L)).willReturn(Optional.of(usuarioObjetivo));
        given(estadoUsuarioRepo.findByNombre("INACTIVO")).willReturn(Optional.of(estado("INACTIVO")));
        given(authentication.getName()).willReturn("admin@correo.com");
        given(usuarioRepo.findByCorreo("admin@correo.com")).willReturn(Optional.of(admin));

        service.cambiarEstado(5L, "INACTIVO", "Solicitud de baja voluntaria", authentication);

        assertThat(usuarioObjetivo.getEstado().getNombre()).isEqualTo("INACTIVO");

        ArgumentCaptor<BitacoraAuditoria> captor = ArgumentCaptor.forClass(BitacoraAuditoria.class);
        verify(bitacoraAuditoriaRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDetalles()).contains("Solicitud de baja voluntaria");
    }

    @Test
    void cambiarEstado_conEstadoInexistenteEnCatalogo_lanzaIllegalArgument() {
        Usuario usuarioObjetivo = usuario(5L, "lector@correo.com", "LECTOR", "ACTIVO");
        given(usuarioRepo.findById(5L)).willReturn(Optional.of(usuarioObjetivo));
        given(estadoUsuarioRepo.findByNombre("SUSPENDIDO")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(5L, "SUSPENDIDO", "motivo", authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUSPENDIDO");
    }

    @Test
    void cambiarEstado_conUsuarioInexistente_lanzaEntityNotFoundYNoConsultaEstados() {
        given(usuarioRepo.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(404L, "INACTIVO", "motivo", authentication))
                .isInstanceOf(EntityNotFoundException.class);

        verify(estadoUsuarioRepo, times(0)).findByNombre(any());
    }

    // ── F8-gerente/V38 ───────────────────────────────────────

    private void comoGerente(String correo, Long id) {
        org.mockito.Mockito.lenient().when(authentication.getName()).thenReturn(correo);
        org.mockito.Mockito.lenient().when(authentication.getAuthorities()).thenAnswer(inv -> List.of(
                (org.springframework.security.core.GrantedAuthority) () -> "ROLE_GERENTE"));
        Usuario gerente = usuario(id, correo, "GERENTE", "ACTIVO");
        org.mockito.Mockito.lenient().when(usuarioRepo.findByCorreo(correo)).thenReturn(Optional.of(gerente));
    }

    @Test
    void gerente_crearLector_guardaCreadoPor() {
        comoGerente("gerente@correo.com", 7L);
        given(rolRepo.findByNombre("LECTOR")).willReturn(Optional.of(rol("LECTOR")));
        given(estadoUsuarioRepo.findByNombre("ACTIVO")).willReturn(Optional.of(estado("ACTIVO")));
        given(usuarioRepo.findByCorreo("nuevo@correo.com")).willReturn(Optional.empty());
        given(usuarioRepo.save(any())).willAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(50L);
            return u;
        });

        service.crearUsuario(new com.uteq.backend.dto.CrearUsuarioAdminRequestDTO(
                "Ana", "Paz", "nuevo@correo.com", "Secreta123", "LECTOR"), authentication);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepo).save(captor.capture());
        assertThat(captor.getValue().getCreadoPor()).isEqualTo(7L);
    }

    @Test
    void gerente_crearGerente_lanzaAccessDenied() {
        comoGerente("gerente@correo.com", 7L);
        given(usuarioRepo.findByCorreo("otro@correo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearUsuario(new com.uteq.backend.dto.CrearUsuarioAdminRequestDTO(
                "Ana", "Paz", "otro@correo.com", "Secreta123", "GERENTE"), authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void gerente_cambiarRolDeOtroGerente_lanzaAccessDenied() {
        comoGerente("gerente@correo.com", 7L);
        Usuario objetivo = usuario(5L, "lector@correo.com", "LECTOR", "ACTIVO");
        objetivo.setCreadoPor(99L);
        given(usuarioRepo.findById(5L)).willReturn(Optional.of(objetivo));
        given(rolRepo.findByNombre("BIBLIOTECARIO")).willReturn(Optional.of(rol("BIBLIOTECARIO")));

        assertThatThrownBy(() -> service.cambiarRol(5L, "BIBLIOTECARIO", authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void gerente_bloquearSuCreado_permite() {
        comoGerente("gerente@correo.com", 7L);
        Usuario objetivo = usuario(5L, "lector@correo.com", "LECTOR", "ACTIVO");
        objetivo.setCreadoPor(7L);
        given(usuarioRepo.findById(5L)).willReturn(Optional.of(objetivo));
        given(estadoUsuarioRepo.findByNombre("INACTIVO")).willReturn(Optional.of(estado("INACTIVO")));

        service.cambiarEstado(5L, "INACTIVO", "Baja", authentication);

        assertThat(objetivo.getEstado().getNombre()).isEqualTo("INACTIVO");
    }
}

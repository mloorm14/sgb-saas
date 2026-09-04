package com.uteq.backend.service;

import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredencialQrServiceTest {

    @Mock UsuarioRepository usuarioRepo;

    @InjectMocks CredencialQrService credencialQrService;

    // ── Test 1: generación de imagen QR ────────────────────
    @Test
    void generarImagenQrPropio_conUsuarioExistente_generaImagenPngNoVacia() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("lector@correo.com");
        Usuario usuario = usuarioConCredencial(1L, UUID.randomUUID(), "ACTIVO");
        given(usuarioRepo.findByCorreo("lector@correo.com")).willReturn(Optional.of(usuario));

        byte[] imagen = credencialQrService.generarImagenQrPropio(auth);

        // No se compara contra bytes exactos (el encoder de ZXing no es
        // determinista pixel a pixel entre versiones) -- solo que produjo
        // una imagen real y no un arreglo vacío o corrupto.
        assertThat(imagen).isNotEmpty();
        assertThat(imagen.length).isGreaterThan(100);
        // Firma PNG estándar: los primeros bytes son siempre 0x89 'P' 'N' 'G'.
        assertThat(imagen[0]).isEqualTo((byte) 0x89);
        assertThat(imagen[1]).isEqualTo((byte) 'P');
        assertThat(imagen[2]).isEqualTo((byte) 'N');
        assertThat(imagen[3]).isEqualTo((byte) 'G');
    }

    // ── Test 2: usuario autenticado no existe ──────────────
    @Test
    void generarImagenQrPropio_conUsuarioInexistente_lanza404() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fantasma@correo.com");
        given(usuarioRepo.findByCorreo("fantasma@correo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> credencialQrService.generarImagenQrPropio(auth))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 3: token válido y usuario activo ──────────────
    @Test
    void resolverPorToken_conTokenValidoYUsuarioActivo_retornaUsuario() {
        UUID token = UUID.randomUUID();
        Usuario usuario = usuarioConCredencial(7L, token, "ACTIVO");
        given(usuarioRepo.findByCredencialQrToken(token)).willReturn(Optional.of(usuario));

        Usuario resultado = credencialQrService.resolverPorToken(token);

        assertThat(resultado.getId()).isEqualTo(7L);
    }

    // ── Test 4: token que no existe en ningún usuario ──────
    @Test
    void resolverPorToken_conTokenInexistente_lanza404() {
        UUID token = UUID.randomUUID();
        given(usuarioRepo.findByCredencialQrToken(token)).willReturn(Optional.empty());

        assertThatThrownBy(() -> credencialQrService.resolverPorToken(token))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 5: usuario bloqueado por multa (no ACTIVO) ────
    @Test
    void resolverPorToken_conUsuarioNoActivo_lanza404() {
        UUID token = UUID.randomUUID();
        Usuario usuario = usuarioConCredencial(8L, token, "BLOQUEADO_POR_MULTA");
        given(usuarioRepo.findByCredencialQrToken(token)).willReturn(Optional.of(usuario));

        assertThatThrownBy(() -> credencialQrService.resolverPorToken(token))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Helpers ────────────────────────────────────────────
    private Usuario usuarioConCredencial(Long id, UUID token, String nombreEstado) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCredencialQrToken(token);
        EstadoUsuario estado = new EstadoUsuario();
        estado.setId(1);
        estado.setNombre(nombreEstado);
        usuario.setEstado(estado);
        return usuario;
    }
}

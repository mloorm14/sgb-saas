package com.uteq.backend.service;

import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.UserRepository;
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
class CredentialQrServiceTest {

    @Mock UserRepository userRepo;

    @InjectMocks CredentialQrService credentialQrService;

    // ── Test 1: generación de imagen QR ────────────────────
    @Test
    void generateImageQrOwn_withUserExisting_generaImagePngNotVacia() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("lector@correo.com");
        User user = userWithCredential(1L, UUID.randomUUID(), "ACTIVO");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(user));

        byte[] image = credentialQrService.generateImageQrOwn(auth);

        // No se compara contra bytes exactos (el encoder de ZXing no es
        // determinista pixel a pixel entre versiones) -- solo que produjo
        // una imagen real y no un arreglo vacío o corrupto.
        assertThat(image).isNotEmpty();
        assertThat(image.length).isGreaterThan(100);
        // Firma PNG estándar: los primeros bytes son siempre 0x89 'P' 'N' 'G'.
        assertThat(image[0]).isEqualTo((byte) 0x89);
        assertThat(image[1]).isEqualTo((byte) 'P');
        assertThat(image[2]).isEqualTo((byte) 'N');
        assertThat(image[3]).isEqualTo((byte) 'G');
    }

    // ── Test 2: usuario autenticado no existe ──────────────
    @Test
    void generateImageQrOwn_withUserInexistente_lanza404() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fantasma@correo.com");
        given(userRepo.findByEmail("fantasma@correo.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialQrService.generateImageQrOwn(auth))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 3: token válido y usuario activo ──────────────
    @Test
    void resolveByToken_withTokenValidYUserActive_retornaUser() {
        UUID token = UUID.randomUUID();
        User user = userWithCredential(7L, token, "ACTIVO");
        given(userRepo.findByCredentialQrToken(token)).willReturn(Optional.of(user));

        User result = credentialQrService.resolveByToken(token);

        assertThat(result.getId()).isEqualTo(7L);
    }

    // ── Test 4: token que no existe en ningún usuario ──────
    @Test
    void resolveByToken_withTokenInexistente_lanza404() {
        UUID token = UUID.randomUUID();
        given(userRepo.findByCredentialQrToken(token)).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialQrService.resolveByToken(token))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 5: usuario bloqueado por multa (no ACTIVO) ────
    @Test
    void resolveByToken_withUserNotActive_lanza404() {
        UUID token = UUID.randomUUID();
        User user = userWithCredential(8L, token, "BLOQUEADO_POR_MULTA");
        given(userRepo.findByCredentialQrToken(token)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> credentialQrService.resolveByToken(token))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Helpers ────────────────────────────────────────────
    private User userWithCredential(Long id, UUID token, String nameStatus) {
        User user = new User();
        user.setId(id);
        user.setCredentialQrToken(token);
        StatusUser status = new StatusUser();
        status.setId(1);
        status.setName(nameStatus);
        user.setStatus(status);
        return user;
    }
}

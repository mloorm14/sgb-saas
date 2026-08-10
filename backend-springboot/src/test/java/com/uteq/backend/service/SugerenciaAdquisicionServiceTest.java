package com.uteq.backend.service;

import com.uteq.backend.dto.SugerenciaAdquisicionRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO;
import com.uteq.backend.entity.SugerenciaAdquisicion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.SugerenciaAdquisicionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SugerenciaAdquisicionServiceTest {

    @Mock SugerenciaAdquisicionRepository sugerenciaRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock Authentication authentication;

    @InjectMocks SugerenciaAdquisicionService sugerenciaService;

    // ── Test 1: crear registra la sugerencia en PENDIENTE ──
    @Test
    void crear_conDatosValidos_quedaEnPendiente() {
        given(authentication.getName()).willReturn("lector@uteq.edu.ec");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec")).willReturn(Optional.of(usuarioConId(7L)));
        given(sugerenciaRepo.save(any())).willAnswer(inv -> {
            SugerenciaAdquisicion s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        SugerenciaAdquisicionResponseDTO resultado = sugerenciaService.crear(requestDTO(), authentication);

        assertThat(resultado.usuarioId()).isEqualTo(7L);
        assertThat(resultado.estado()).isEqualTo(SugerenciaAdquisicion.PENDIENTE);
        assertThat(resultado.titulo()).isEqualTo("Clean Architecture");
    }

    // ── Test 2: listarPropias filtra por el usuario autenticado ──
    @Test
    void listarPropias_devuelveSoloLasDelUsuarioAutenticado() {
        given(authentication.getName()).willReturn("lector@uteq.edu.ec");
        given(usuarioRepo.findByCorreo("lector@uteq.edu.ec")).willReturn(Optional.of(usuarioConId(7L)));
        Page<SugerenciaAdquisicion> pagina = new PageImpl<>(List.of(sugerenciaConId(1L, 7L)));
        given(sugerenciaRepo.findByUsuarioId(7L, Pageable.unpaged())).willReturn(pagina);

        Page<SugerenciaAdquisicionResponseDTO> resultado =
                sugerenciaService.listarPropias(authentication, Pageable.unpaged());

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).usuarioId()).isEqualTo(7L);
    }

    // ── Test 3: listarTodas sin filtro de estado trae todas ──
    @Test
    void listarTodas_sinFiltroEstado_traeTodas() {
        Page<SugerenciaAdquisicion> pagina = new PageImpl<>(List.of(sugerenciaConId(1L, 7L)));
        given(sugerenciaRepo.findAll(Pageable.unpaged())).willReturn(pagina);

        Page<SugerenciaAdquisicionResponseDTO> resultado =
                sugerenciaService.listarTodas(null, Pageable.unpaged());

        assertThat(resultado.getTotalElements()).isEqualTo(1);
    }

    // ── Test 4: cambiarEstado a APROBADA registra quien revisó ──
    @Test
    void cambiarEstado_aAprobada_registraRevisor() {
        SugerenciaAdquisicion sugerencia = sugerenciaConId(1L, 7L);
        given(sugerenciaRepo.findById(1L)).willReturn(Optional.of(sugerencia));
        given(authentication.getName()).willReturn("gerente@uteq.edu.ec");
        given(usuarioRepo.findByCorreo("gerente@uteq.edu.ec")).willReturn(Optional.of(usuarioConId(3L)));
        given(sugerenciaRepo.save(sugerencia)).willReturn(sugerencia);

        SugerenciaAdquisicionResponseDTO resultado =
                sugerenciaService.cambiarEstado(1L, "APROBADA", authentication);

        assertThat(resultado.estado()).isEqualTo("APROBADA");
        assertThat(resultado.revisadoPor()).isEqualTo(3L);
    }

    // ── Test 5: cambiarEstado sobre una sugerencia inexistente lanza 404 ──
    @Test
    void cambiarEstado_cuandoNoExiste_lanzaEntityNotFound() {
        given(sugerenciaRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sugerenciaService.cambiarEstado(99L, "APROBADA", authentication))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── Helpers ───────────────────────────────────────────
    private Usuario usuarioConId(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private SugerenciaAdquisicion sugerenciaConId(Long id, Long usuarioId) {
        SugerenciaAdquisicion s = new SugerenciaAdquisicion();
        s.setId(id);
        s.setUsuarioId(usuarioId);
        s.setTitulo("Clean Architecture");
        s.setEstado(SugerenciaAdquisicion.PENDIENTE);
        return s;
    }

    private SugerenciaAdquisicionRequestDTO requestDTO() {
        return new SugerenciaAdquisicionRequestDTO(
                "Clean Architecture", "Robert C. Martin", null, "Complementa el material de POO"
        );
    }
}

package com.uteq.backend.service;

import com.uteq.backend.dto.SuggestionAcquisitionRequestDTO;
import com.uteq.backend.dto.SuggestionAcquisitionResponseDTO;
import com.uteq.backend.entity.SuggestionAcquisition;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.SuggestionAcquisitionRepository;
import com.uteq.backend.repository.UserRepository;
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
class SuggestionAcquisitionServiceTest {

    @Mock SuggestionAcquisitionRepository suggestionRepo;
    @Mock UserRepository userRepo;
    @Mock Authentication authentication;

    @InjectMocks SuggestionAcquisitionService suggestionService;

    // ── Test 1: crear registra la sugerencia en PENDIENTE ──
    @Test
    void create_withDataValids_quedaPending() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        given(suggestionRepo.save(any())).willAnswer(inv -> {
            SuggestionAcquisition s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        SuggestionAcquisitionResponseDTO result = suggestionService.create(requestDTO(), authentication);

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.status()).isEqualTo(SuggestionAcquisition.PENDIENTE);
        assertThat(result.title()).isEqualTo("Clean Architecture");
    }

    // ── Test 2: listarPropias filtra por el usuario autenticado ──
    @Test
    void listOwns_devuelveSoloUserAutenticado() {
        given(authentication.getName()).willReturn("lector@correo.com");
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(7L)));
        Page<SuggestionAcquisition> page = new PageImpl<>(List.of(suggestionWithId(1L, 7L)));
        given(suggestionRepo.findByUserId(7L, Pageable.unpaged())).willReturn(page);

        Page<SuggestionAcquisitionResponseDTO> result =
                suggestionService.listOwns(authentication, Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo(7L);
    }

    // ── Test 3: listarTodas sin filtro de estado trae todas ──
    @Test
    void listTodas_withoutFilterStatus_traeTodas() {
        Page<SuggestionAcquisition> page = new PageImpl<>(List.of(suggestionWithId(1L, 7L)));
        given(suggestionRepo.findAll(Pageable.unpaged())).willReturn(page);

        Page<SuggestionAcquisitionResponseDTO> result =
                suggestionService.listTodas(null, Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── Test 4: cambiarEstado a APROBADA registra quien revisó ──
    @Test
    void changeStatus_aAprobada_registraRevisor() {
        SuggestionAcquisition suggestion = suggestionWithId(1L, 7L);
        given(suggestionRepo.findById(1L)).willReturn(Optional.of(suggestion));
        given(authentication.getName()).willReturn("gerente@correo.com");
        given(userRepo.findByEmail("gerente@correo.com")).willReturn(Optional.of(userWithId(3L)));
        given(suggestionRepo.save(suggestion)).willReturn(suggestion);

        SuggestionAcquisitionResponseDTO result =
                suggestionService.changeStatus(1L, "APROBADA", authentication);

        assertThat(result.status()).isEqualTo("APROBADA");
        assertThat(result.revisadoBy()).isEqualTo(3L);
    }

    // ── Test 5: cambiarEstado sobre una sugerencia inexistente lanza 404 ──
    @Test
    void changeStatus_cuandoNotExiste_lanzaEntityNotFound() {
        given(suggestionRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionService.changeStatus(99L, "APROBADA", authentication))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── Test 6: confirmarAdquisicion pasa a APROBADA todas las PENDIENTE del ISBN ──
    @Test
    void confirmAcquisition_withPendientes_Aprueba() {
        SuggestionAcquisition s1 = suggestionWithId(1L, 7L);
        s1.setIsbn("9781449373320");
        SuggestionAcquisition s2 = suggestionWithId(2L, 9L);
        s2.setIsbn("9781449373320");
        given(suggestionRepo.findByIsbnAndStatus("9781449373320", SuggestionAcquisition.PENDIENTE))
                .willReturn(java.util.List.of(s1, s2));

        int confirmadas = suggestionService.confirmAcquisition("9781449373320", 3L);

        assertThat(confirmadas).isEqualTo(2);
        assertThat(s1.getStatus()).isEqualTo(SuggestionAcquisition.APROBADA);
        assertThat(s2.getStatus()).isEqualTo(SuggestionAcquisition.APROBADA);
        assertThat(s1.getRevisadoBy()).isEqualTo(3L);
    }

    // ── Test 7: confirmarAdquisicion sin pendientes retorna 0 ──
    @Test
    void confirmAcquisition_withoutPendientes_retornaCero() {
        given(suggestionRepo.findByIsbnAndStatus("9781449373320", SuggestionAcquisition.PENDIENTE))
                .willReturn(java.util.List.of());

        assertThat(suggestionService.confirmAcquisition("9781449373320", 3L)).isZero();
    }

    // ── Test 8: getMasPedidos pasa el Pageable sin sort (el orden vive en
    // el JPQL; Sort.by("cantidad") revienta con UnknownPathException) ──
    @Test
    void getMostPedidos_pasaPageableWithoutSort() {
        org.springframework.data.domain.Page<com.uteq.backend.dto.SuggestionGroupedDTO> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        given(suggestionRepo.findMostPedidosAgrupados(org.mockito.ArgumentMatchers.any()))
                .willReturn(page);

        suggestionService.getMostPedidos(org.springframework.data.domain.PageRequest.of(1, 20));

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.verify(suggestionRepo).findMostPedidosAgrupados(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().isSorted()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────
    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private SuggestionAcquisition suggestionWithId(Long id, Long userId) {
        SuggestionAcquisition s = new SuggestionAcquisition();
        s.setId(id);
        s.setUserId(userId);
        s.setTitle("Clean Architecture");
        s.setStatus(SuggestionAcquisition.PENDIENTE);
        return s;
    }

    private SuggestionAcquisitionRequestDTO requestDTO() {
        return new SuggestionAcquisitionRequestDTO(
                "Clean Architecture", "Robert C. Martin", null, "Complementa el material de POO"
        );
    }
}

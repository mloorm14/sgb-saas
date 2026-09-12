package com.uteq.backend.service;

import com.uteq.backend.dto.EventAuditResponseDTO;
import com.uteq.backend.entity.AuditLogAudit;
import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.AuditLogAuditRepository;
import com.uteq.backend.repository.UserRepository;
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
class AuditServiceTest {

    @Mock AuditLogAuditRepository auditLogAuditRepo;
    @Mock UserRepository userRepo;

    @InjectMocks AuditService service;

    private User user(Long id, String email) {
        Instant ahora = Instant.now();
        StatusUser active = new StatusUser();
        active.setId(1);
        active.setName("ACTIVO");
        Role reader = new Role();
        reader.setId(1);
        reader.setName("LECTOR");
        return User.builder()
                .id(id)
                .name("Nombre")
                .lastName("Apellido")
                .email(email)
                .passwordHash("hash")
                .status(active)
                .emailVerified(true)
                .roles(Set.of(reader))
                .dateRegistration(ahora)
                .updated(ahora)
                .build();
    }

    private AuditLogAudit event(Long id, Long userId, String typeOperacion,
                                      String tableAfectada, String detalles) {
        return AuditLogAudit.builder()
                .id(id)
                .userId(userId)
                .typeOperacion(typeOperacion)
                .tableAfectada(tableAfectada)
                .registrationId(userId)
                .detalles(detalles)
                .dateTime(OffsetDateTime.now())
                .build();
    }

    @Test
    void list_resuelveEmailEventsWithUserIdConocido() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLogAudit evt = event(1L, 9L, "UPDATE", "usuarios", "Cambio de rol");
        Page<AuditLogAudit> page = new PageImpl<>(List.of(evt), pageable, 1);

        given(auditLogAuditRepo.searchWithFilters(null, null, null, null, pageable))
                .willReturn(page);
        given(userRepo.findAllById(Set.of(9L))).willReturn(List.of(user(9L, "admin@correo.com")));

        Page<EventAuditResponseDTO> result = service.list(null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).user()).isEqualTo("admin@correo.com");
        assertThat(result.getContent().get(0).action()).isEqualTo("UPDATE");
        assertThat(result.getContent().get(0).module()).isEqualTo("usuarios");
    }

    // LOGIN_FAIL se registra con usuarioId null (AuthService.registrarAuditoria)
    // -- el DTO debe reflejar eso como usuario null, no romper ni inventar un
    // valor, y no debe ni siquiera consultar UsuarioRepository para ids vacíos.
    @Test
    void list_withUserIdNulo_dejaUserNuloYNotConsultaUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLogAudit evt = event(2L, null, "LOGIN_FAIL", "usuarios", "Login fallido para correo: x@x.com");
        Page<AuditLogAudit> page = new PageImpl<>(List.of(evt), pageable, 1);

        given(auditLogAuditRepo.searchWithFilters(null, null, null, null, pageable))
                .willReturn(page);
        given(userRepo.findAllById(Set.of())).willReturn(List.of());

        Page<EventAuditResponseDTO> result = service.list(null, null, null, null, pageable);

        assertThat(result.getContent().get(0).user()).isNull();
        assertThat(result.getContent().get(0).action()).isEqualTo("LOGIN_FAIL");
    }

    @Test
    void list_withFiltersUserModuleYDate_lospasaTalCualRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime until = OffsetDateTime.now();

        given(auditLogAuditRepo.searchWithFilters(eq(9L), eq("usuarios"), eq(from), eq(until), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(9L, "usuarios", from, until, pageable);

        verify(auditLogAuditRepo, times(1))
                .searchWithFilters(9L, "usuarios", from, until, pageable);
    }

    @Test
    void list_withoutResults_notConsultaUsersYRetornaPageVacia() {
        Pageable pageable = PageRequest.of(0, 20);
        given(auditLogAuditRepo.searchWithFilters(any(), any(), any(), any(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<EventAuditResponseDTO> result = service.list(null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(userRepo, times(1)).findAllById(Set.of());
    }
}

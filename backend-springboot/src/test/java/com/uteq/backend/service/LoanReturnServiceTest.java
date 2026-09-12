package com.uteq.backend.service;

import com.uteq.backend.dto.LoanReturnFullResponseDTO;
import com.uteq.backend.dto.LoanReturnHistoryDTO;
import com.uteq.backend.dto.LoanReturnRequestDTO;
import com.uteq.backend.dto.TypeDamageDTO;
import com.uteq.backend.entity.StatusFine;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Fine;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.RegistrationDamage;
import com.uteq.backend.entity.TypeDamage;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.EvidenceDamageRepository;
import com.uteq.backend.repository.StatusFineRepository;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.LoanProcedureRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.RegistrationDamageDetailRepository;
import com.uteq.backend.repository.RegistrationDamageRepository;
import com.uteq.backend.repository.TypeDamageRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class LoanReturnServiceTest {

    @Mock LoanRepository loanRepo;
    @Mock LoanProcedureRepository loanProcRepo;
    @Mock UserRepository userRepo;
    @Mock BookRepository bookRepo;
    @Mock StatusLoanRepository statusLoanRepo;
    @Mock StatusFineRepository statusFineRepo;
    @Mock FineRepository fineRepo;
    @Mock TypeDamageRepository typeDamageRepo;
    @Mock RegistrationDamageRepository registrationDamageRepo;
    @Mock RegistrationDamageDetailRepository registrationDamageDetailRepo;
    @Mock EvidenceDamageRepository evidenceDamageRepo;

    @InjectMocks LoanReturnService loanReturnService;

    // ── Test 1: devolución SIN atraso ni daño ───────────
    @Test
    void registerLoanReturn_withoutAtrasoWithoutDamage_notGeneraFine() {
        Long loanId = 1L;
        Long librarianId = 10L;

        Loan loan = loanWithId(loanId);
        loan.setDateLoanReturnReal(null);

        Map<String, Object> spResult = new HashMap<>();
        spResult.put("o_hubo_multa", false);
        spResult.put("o_monto_multa", null);

        given(loanRepo.findById(loanId)).willReturn(Optional.of(loan));
        given(loanProcRepo.spRegisterLoanReturn(loanId)).willReturn(spResult);

        LoanReturnRequestDTO dto = new LoanReturnRequestDTO(
                "BUEN_ESTADO", null, null);

        LoanReturnFullResponseDTO result =
                loanReturnService.registerLoanReturn(loanId, dto, librarianId);

        assertThat(result.loanId()).isEqualTo(loanId);
        assertThat(result.huboFineAtraso()).isFalse();
        assertThat(result.huboFineDamage()).isFalse();
        assertThat(result.amountTotal()).isZero();
    }

    // ── Test 2: devolución CON daño ──────────────────────
    @Test
    void registerLoanReturn_withDamage_generaFineDamage() {
        Long loanId = 2L;
        Long librarianId = 10L;

        Loan loan = loanWithId(loanId);
        loan.setDateLoanReturnReal(null);

        Map<String, Object> spResult = new HashMap<>();
        spResult.put("o_hubo_multa", false);
        spResult.put("o_monto_multa", null);

        StatusFine statusFine = new StatusFine();
        statusFine.setId(1);

        given(loanRepo.findById(loanId)).willReturn(Optional.of(loan));
        given(loanProcRepo.spRegisterLoanReturn(loanId)).willReturn(spResult);
        given(statusFineRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusFine));
        given(registrationDamageRepo.save(any())).willAnswer(inv -> {
            RegistrationDamage rd = inv.getArgument(0);
            rd.setId(100L);
            return rd;
        });

        LoanReturnRequestDTO.DamageItemDTO damageItem = new LoanReturnRequestDTO.DamageItemDTO(
                1, null, new BigDecimal("5.00"));

        LoanReturnRequestDTO dto = new LoanReturnRequestDTO(
                "CON_DANO", "Pagina rasgada", List.of(damageItem));

        LoanReturnFullResponseDTO result =
                loanReturnService.registerLoanReturn(loanId, dto, librarianId);

        assertThat(result.huboFineDamage()).isTrue();
        assertThat(result.amountFineDamage()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(result.amountTotal()).isEqualByComparingTo(new BigDecimal("5.00"));
        verify(fineRepo).save(any(Fine.class));
    }

    // ── Test 3: devolución con préstamo YA devuelto ──────
    @Test
    void registerLoanReturn_loanYaDevuelto_lanzaException() {
        Long loanId = 3L;
        Loan loan = loanWithId(loanId);
        loan.setDateLoanReturnReal(OffsetDateTime.now());

        given(loanRepo.findById(loanId)).willReturn(Optional.of(loan));

        LoanReturnRequestDTO dto = new LoanReturnRequestDTO(
                "BUEN_ESTADO", null, null);

        assertThatThrownBy(() -> loanReturnService.registerLoanReturn(loanId, dto, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue devuelto");
    }

    // ── Test 4: préstamo no encontrado ──────────────────
    @Test
    void registerLoanReturn_loanNotFound_lanzaEntityNotFound() {
        given(loanRepo.findById(999L)).willReturn(Optional.empty());

        LoanReturnRequestDTO dto = new LoanReturnRequestDTO(
                "BUEN_ESTADO", null, null);

        assertThatThrownBy(() -> loanReturnService.registerLoanReturn(999L, dto, 10L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── Test 5: listar tipos de daño ────────────────────
    @Test
    void listTypesDamage_devuelveSoloActives() {
        TypeDamage active = new TypeDamage();
        active.setId(1);
        active.setName("Rasgado");
        active.setPrice(new BigDecimal("5.00"));
        active.setActive(true);

        TypeDamage inactivo = new TypeDamage();
        inactivo.setId(2);
        inactivo.setName("Manchado");
        inactivo.setPrice(new BigDecimal("3.00"));
        inactivo.setActive(false);

        given(typeDamageRepo.findByActiveTrue()).willReturn(List.of(active));

        List<TypeDamageDTO> result = loanReturnService.listTypesDamage();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Rasgado");
        assertThat(result.get(0).price()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    // ── Test 6: devolución LIBRO PERDIDO ─────────────────
    @Test
    void registerLoanReturn_bookPerdido_generaFineValueBook() {
        Long loanId = 4L;
        Long librarianId = 10L;

        Loan loan = loanWithId(loanId);
        loan.setDateLoanReturnReal(null);
        loan.setBookId(42L);

        Map<String, Object> spResult = new HashMap<>();
        spResult.put("o_hubo_multa", false);
        spResult.put("o_monto_multa", null);

        StatusFine statusFine = new StatusFine();
        statusFine.setId(1);

        Book book = new Book();
        book.setId(42L);

        given(loanRepo.findById(loanId)).willReturn(Optional.of(loan));
        given(loanProcRepo.spRegisterLoanReturn(loanId)).willReturn(spResult);
        given(statusFineRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusFine));
        given(bookRepo.findById(42L)).willReturn(Optional.of(book));
        given(registrationDamageRepo.save(any())).willAnswer(inv -> {
            RegistrationDamage rd = inv.getArgument(0);
            rd.setId(200L);
            return rd;
        });

        LoanReturnRequestDTO dto = new LoanReturnRequestDTO(
                "PERDIDO", "Se perdio el libro", null);

        LoanReturnFullResponseDTO result =
                loanReturnService.registerLoanReturn(loanId, dto, librarianId);

        assertThat(result.huboFineDamage()).isTrue();
        assertThat(result.amountFineDamage()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(result.damagesRegistrados()).hasSize(1);
        assertThat(result.damagesRegistrados().get(0).typeDamageName()).isEqualTo("Libro perdido");
    }

    // ── helpers ─────────────────────────────────────────

    private Loan loanWithId(Long id) {
        Loan p = new Loan();
        p.setId(id);
        p.setUserId(1L);
        p.setBookId(1L);
        p.setDateLoan(OffsetDateTime.now().minusDays(5));
        p.setDateLoanReturnEstimada(OffsetDateTime.now().plusDays(2));
        p.setDateLoanReturnReal(null);
        return p;
    }
}

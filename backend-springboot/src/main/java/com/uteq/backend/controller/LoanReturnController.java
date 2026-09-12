package com.uteq.backend.controller;

import com.uteq.backend.dto.LoanReturnFullResponseDTO;
import com.uteq.backend.dto.LoanReturnHistoryDTO;
import com.uteq.backend.dto.LoanReturnRequestDTO;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.service.LoanReturnService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devoluciones")
public class LoanReturnController {

    private final LoanReturnService loanReturnService;
    private final UserRepository userRepo;

    public LoanReturnController(LoanReturnService loanReturnService,
                                UserRepository userRepo) {
        this.loanReturnService = loanReturnService;
        this.userRepo = userRepo;
    }

    @PostMapping("/prestamo/{prestamoId}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Registers Response Entity&lt;Devolucion Completa Response DTO>.
     *
     * @param loanId numeric identifier used to scope this Response Entity&lt;Devolucion Completa Response DTO>
     * @param dto return Request data transfer object used to scope this Response Entity&lt;Devolucion Completa Response DTO>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Devolucion Completa Response DTO>
     * @return Response Entity&lt;Devolucion Completa Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<LoanReturnFullResponseDTO> registerLoanReturn(
            @PathVariable("prestamoId") Long loanId,
            @Valid @RequestBody LoanReturnRequestDTO dto,
            Authentication authentication) {
        Long librarianId = resolveIdByEmail(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanReturnService.registerLoanReturn(loanId, dto, librarianId));
    }

    @GetMapping("/historial")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Handles history Devoluciones.
     *
     * @param authentication authentication of the caller used to scope this history Devoluciones
     * @return Response Entity&lt;List<Devolucion history DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<LoanReturnHistoryDTO>> historyLoanReturns(
            Authentication authentication) {
        Long librarianId = resolveIdByEmail(authentication.getName());
        return ResponseEntity.ok(loanReturnService.historyLoanReturns(librarianId));
    }

    private Long resolveIdByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + email));
        return user.getId();
    }
}

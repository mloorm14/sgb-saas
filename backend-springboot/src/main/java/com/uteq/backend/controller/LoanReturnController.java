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
     * Registra register loan return validando los datos de entrada antes de persistir cambios.
     *
     * @param loanId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
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
     * Procesa history loan returns y devuelve el resultado calculado por el backend.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
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

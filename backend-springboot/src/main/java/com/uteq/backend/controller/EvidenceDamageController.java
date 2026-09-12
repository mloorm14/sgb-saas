package com.uteq.backend.controller;

import com.uteq.backend.dto.EvidenceDamageResponseDTO;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.service.LoanReturnService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devoluciones")
public class EvidenceDamageController {

    private final LoanReturnService loanReturnService;
    private final UserRepository userRepo;

    public EvidenceDamageController(LoanReturnService loanReturnService,
                                    UserRepository userRepo) {
        this.loanReturnService = loanReturnService;
        this.userRepo = userRepo;
    }
    /**
     * Procesa upload evidence y devuelve el resultado calculado por el backend.
     *
     * @param registrationDamageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param file archivo recibido en la peticion y usado como contenido principal de la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @PostMapping(value = "/evidencia/{registroDanoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<EvidenceDamageResponseDTO> uploadEvidence(
            @PathVariable("registroDanoId") Long registrationDamageId,
            @RequestParam("archivo") MultipartFile file,
            Authentication authentication) {
        Long librarianId = resolveIdByEmail(authentication.getName());
        return ResponseEntity.ok(loanReturnService.uploadEvidence(registrationDamageId, file, librarianId));
    }

    @GetMapping("/evidencia/{registroDanoId}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Consulta list evidences usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param registrationDamageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<List<EvidenceDamageResponseDTO>> listEvidences(
            @PathVariable("registroDanoId") Long registrationDamageId) {
        return ResponseEntity.ok(loanReturnService.listEvidences(registrationDamageId));
    }

    @GetMapping("/evidencia/{id}/archivo")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Consulta get file usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<byte[]> getFile(@PathVariable Long id) {
        var evidence = loanReturnService.getFileBinario(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidence.fileType()))
                .body(evidence.fileBytes());
    }

    private Long resolveIdByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + email));
        return user.getId();
    }
}

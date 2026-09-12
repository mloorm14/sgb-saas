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
     * Lists Response Entity&lt;List<Evidencia damage report Response DTO>>.
     *
     * @param registroDanoId numeric identifier used to scope this Response Entity&lt;List<Evidencia damage report Response DTO>>
     * @return Response Entity&lt;List<Evidencia damage report Response DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<EvidenceDamageResponseDTO>> listEvidences(
            @PathVariable("registroDanoId") Long registrationDamageId) {
        return ResponseEntity.ok(loanReturnService.listEvidences(registrationDamageId));
    }

    @GetMapping("/evidencia/{id}/archivo")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Retrieves Response Entity&lt;byte[]>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;byte[]>
     * @return Response Entity&lt;byte[]> reflecting the state after the operation
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

package com.uteq.backend.controller;

import com.uteq.backend.dto.AuthorRequestDTO;
import com.uteq.backend.dto.AuthorResponseDTO;
import com.uteq.backend.entity.Author;
import com.uteq.backend.repository.AuthorRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/autores")
public class AuthorController {

    private final AuthorRepository authorRepository;

    public AuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping
    /**
     * Lists author.
     *
     * @return response entity<list<autor response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<AuthorResponseDTO>> list() {
        List<AuthorResponseDTO> authors = authorRepository.findAll().stream()
                .map(a -> new AuthorResponseDTO(a.getId(), a.getName()))
                .toList();
        return ResponseEntity.ok(authors);
    }

    @GetMapping("/buscar")
    /**
     * Searches Response Entity&lt;List<Autor Response DTO>>.
     *
     * @param q text value used to scope this Response Entity&lt;List<Autor Response DTO>>
     * @return Response Entity&lt;List<Autor Response DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<AuthorResponseDTO>> search(@RequestParam String q) {
        return ResponseEntity.ok(
                authorRepository.findTop5ByNameContainingIgnoreCase(q).stream()
                        .map(a -> new AuthorResponseDTO(a.getId(), a.getName()))
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Creates Response Entity&lt;Autor Response DTO>.
     *
     * @param dto author Request data transfer object used to scope this Response Entity&lt;Autor Response DTO>
     * @return Response Entity&lt;Autor Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<AuthorResponseDTO> create(@Valid @RequestBody AuthorRequestDTO dto) {
        Author author = new Author();
        author.setName(dto.name());
        Author guardado = authorRepository.save(author);
        return ResponseEntity.created(URI.create("/api/v1/autores/" + guardado.getId()))
                .body(new AuthorResponseDTO(guardado.getId(), guardado.getName()));
    }
}

package com.uteq.backend.repository;

import com.uteq.backend.entity.Favorito;
import com.uteq.backend.entity.FavoritoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

// JpaRepository<Favorito, FavoritoId>: la PK compuesta (@IdClass en
// Favorito) se referencia acá con la clase auxiliar FavoritoId, no con
// Long -- mismo mecanismo que cualquier entidad con @IdClass en Spring
// Data JPA.
@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, FavoritoId> {

    List<Favorito> findByUsuarioId(Long usuarioId);

    Page<Favorito> findByUsuarioId(Long usuarioId, Pageable pageable);

    boolean existsByUsuarioIdAndLibroId(Long usuarioId, Long libroId);

    void deleteByUsuarioIdAndLibroId(Long usuarioId, Long libroId);
}

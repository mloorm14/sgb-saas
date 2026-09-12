package com.uteq.backend.repository;

import com.uteq.backend.entity.Favorite;
import com.uteq.backend.entity.FavoriteId;
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
public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    List<Favorite> findByUserId(Long userId);

    Page<Favorite> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndBookId(Long userId, Long bookId);

    void deleteByUserIdAndBookId(Long userId, Long bookId);
}

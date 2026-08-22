package com.uteq.backend.repository;

import com.uteq.backend.entity.Autor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutorRepository extends JpaRepository<Autor, Long> {
    List<Autor> findTop5ByNombreContainingIgnoreCase(String nombre);
}

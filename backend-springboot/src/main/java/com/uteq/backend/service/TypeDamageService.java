package com.uteq.backend.service;

import com.uteq.backend.dto.TypeDamageDTO;
import com.uteq.backend.entity.CategoryDamage;
import com.uteq.backend.entity.TypeDamage;
import com.uteq.backend.repository.CategoryDamageRepository;
import com.uteq.backend.repository.TypeDamageRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TypeDamageService {

    private final TypeDamageRepository typeDamageRepo;
    private final CategoryDamageRepository categoryDamageRepo;

    private TypeDamageDTO toDTO(TypeDamage t) {
        return new TypeDamageDTO(t.getId(), t.getName(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getTypeCost(), t.getValue());
    }

    @Transactional(readOnly = true)
    /**
         * Lista todos los backups registrados.
     * @return lista de backups ordenados por fecha descendente
     */
    public List<TypeDamageDTO> listAll() {
        return typeDamageRepo.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    /**
         * Lista programaciones de backup activas.
     * @return lista de programaciones activas
     */
    public List<TypeDamageDTO> listActives() {
        return typeDamageRepo.findByActiveTrue().stream().map(this::toDTO).toList();
    }

    @Transactional
    /**
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param name valor de entrada name usado por la operacion para completar su regla de negocio
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param typeCost valor de entrada typeCost usado por la operacion para completar su regla de negocio
     * @param value clave o valor de configuracion que se valida antes de guardarse
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public TypeDamageDTO create(String name, Integer categoryId, String typeCost, BigDecimal value) {
        if (typeDamageRepo.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Ya existe un tipo de daño con el nombre: " + name);
        }
        CategoryDamage cat = categoryDamageRepo.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Categoría de daño no encontrada: " + categoryId));
        validate(typeCost, value);
        TypeDamage type = new TypeDamage();
        type.setName(name);
        type.setCategory(cat);
        type.setTypeCost(typeCost);
        type.setValue(value);
        type.setActive(true);
        TypeDamage guardado = typeDamageRepo.save(type);
        return toDTO(guardado);
    }

    @Transactional
    /**
     * Actualiza update con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param name valor de entrada name usado por la operacion para completar su regla de negocio
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param typeCost valor de entrada typeCost usado por la operacion para completar su regla de negocio
     * @param value clave o valor de configuracion que se valida antes de guardarse
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public TypeDamageDTO update(Integer id, String name, Integer categoryId, String typeCost, BigDecimal value) {
        TypeDamage type = typeDamageRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        typeDamageRepo.findByName(name)
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> { throw new IllegalArgumentException("Ya existe otro tipo de daño con el nombre: " + name); });
        CategoryDamage cat = categoryDamageRepo.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Categoría de daño no encontrada: " + categoryId));
        validate(typeCost, value);
        type.setName(name);
        type.setCategory(cat);
        type.setTypeCost(typeCost);
        type.setValue(value);
        TypeDamage guardado = typeDamageRepo.save(type);
        return toDTO(guardado);
    }

    private void validate(String typeCost, BigDecimal value) {
        if (!"FIJO".equals(typeCost) && !"PORCENTAJE".equals(typeCost)) {
            throw new IllegalArgumentException("tipoCosto debe ser FIJO o PORCENTAJE");
        }
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("valor debe ser >=0");
        if ("PORCENTAJE".equals(typeCost) && value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("porcentaje no puede superar 100");
    }

    @Transactional
    /**
     * Elimina o anula delete despues de validar que la operacion sea permitida.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     */
    public void delete(Integer id) {
        TypeDamage type = typeDamageRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        type.setActive(false);
        typeDamageRepo.save(type);
    }
}

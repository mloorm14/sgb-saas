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
     * Lists Tipo damage report DTO records.
     *
     * @return list of Tipo damage report data transfer object matching the requested criteria
     */
    public List<TypeDamageDTO> listAll() {
        return typeDamageRepo.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    /**
     * Lists Tipo damage report DTO records.
     *
     * @return list of Tipo damage report data transfer object matching the requested criteria
     */
    public List<TypeDamageDTO> listActives() {
        return typeDamageRepo.findByActiveTrue().stream().map(this::toDTO).toList();
    }

    @Transactional
    /**
     * Creates Tipo damage report data transfer object.
     *
     * @param nombre text value used to scope this Tipo damage report data transfer object
     * @param categoryId numeric value used to scope this Tipo damage report data transfer object
     * @param tipoCosto text value used to scope this Tipo damage report data transfer object
     * @param valor monetary amount used to scope this Tipo damage report data transfer object
     * @return Tipo damage report data transfer object reflecting the state after the operation
     * @throws IllegalArgumentException when the Tipo damage report data transfer object cannot be processed with the given input
     * @throws EntityNotFoundException when the Tipo damage report data transfer object cannot be processed with the given input
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
     * Updates Tipo damage report data transfer object.
     *
     * @param id numeric value used to scope this Tipo damage report data transfer object
     * @param nombre text value used to scope this Tipo damage report data transfer object
     * @param categoryId numeric value used to scope this Tipo damage report data transfer object
     * @param tipoCosto text value used to scope this Tipo damage report data transfer object
     * @param valor monetary amount used to scope this Tipo damage report data transfer object
     * @return Tipo damage report data transfer object reflecting the state after the operation
     * @throws IllegalArgumentException when the Tipo damage report data transfer object cannot be processed with the given input
     * @throws EntityNotFoundException when the Tipo damage report data transfer object cannot be processed with the given input
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
     * Deletes Tipo damage report.
     *
     * @param id numeric value used to scope this Tipo damage report
     * @throws EntityNotFoundException when the Tipo damage report cannot be processed with the given input
     */
    public void delete(Integer id) {
        TypeDamage type = typeDamageRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tipo de daño no encontrado: " + id));
        type.setActive(false);
        typeDamageRepo.save(type);
    }
}

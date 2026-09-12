package com.uteq.backend.service;

import com.uteq.backend.dto.TypeDamageDTO;
import com.uteq.backend.entity.CategoryDamage;
import com.uteq.backend.entity.TypeDamage;
import com.uteq.backend.repository.CategoryDamageRepository;
import com.uteq.backend.repository.TypeDamageRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TypeDamageServiceTest {

    @Mock TypeDamageRepository typeDamageRepo;
    @Mock CategoryDamageRepository categoryDamageRepo;

    @Test
    void listAllYListActives_mapeanCategoriaOpcional() {
        TypeDamageService service = service();
        TypeDamage conCategoria = typeDamage(1, "Ruptura", category(2, "Fisico"), "FIJO", "10.00");
        TypeDamage sinCategoria = typeDamage(2, "Otro", null, "PORCENTAJE", "5.00");
        given(typeDamageRepo.findAll()).willReturn(List.of(conCategoria, sinCategoria));
        given(typeDamageRepo.findByActiveTrue()).willReturn(List.of(conCategoria));

        List<TypeDamageDTO> all = service.listAll();
        List<TypeDamageDTO> actives = service.listActives();

        assertThat(all).extracting(TypeDamageDTO::categoryId).containsExactly(2, null);
        assertThat(all).extracting(TypeDamageDTO::categoryName).containsExactly("Fisico", null);
        assertThat(actives).hasSize(1);
    }

    @Test
    void create_cuandoDatosValidos_guardaActivo() {
        TypeDamageService service = service();
        CategoryDamage category = category(3, "Uso");
        given(typeDamageRepo.findByName("Mancha")).willReturn(Optional.empty());
        given(categoryDamageRepo.findById(3)).willReturn(Optional.of(category));
        given(typeDamageRepo.save(any(TypeDamage.class))).willAnswer(inv -> {
            TypeDamage saved = inv.getArgument(0);
            saved.setId(9);
            return saved;
        });

        TypeDamageDTO result = service.create("Mancha", 3, "FIJO", BigDecimal.valueOf(4));

        assertThat(result.id()).isEqualTo(9);
        assertThat(result.name()).isEqualTo("Mancha");
        assertThat(result.categoryId()).isEqualTo(3);
        assertThat(result.typeCost()).isEqualTo("FIJO");
        assertThat(result.value()).isEqualByComparingTo("4");
    }

    @Test
    void create_rechazaNombreDuplicadoCategoriaFaltanteYValoresInvalidos() {
        TypeDamageService service = service();
        given(typeDamageRepo.findByName("Duplicado")).willReturn(Optional.of(new TypeDamage()));
        given(typeDamageRepo.findByName("Sin categoria")).willReturn(Optional.empty());
        given(typeDamageRepo.findByName("Tipo malo")).willReturn(Optional.empty());
        given(typeDamageRepo.findByName("Valor negativo")).willReturn(Optional.empty());
        given(typeDamageRepo.findByName("Porcentaje alto")).willReturn(Optional.empty());
        given(categoryDamageRepo.findById(99)).willReturn(Optional.empty());
        given(categoryDamageRepo.findById(1)).willReturn(Optional.of(category(1, "Fisico")));

        assertThatThrownBy(() -> service.create("Duplicado", 1, "FIJO", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> service.create("Sin categoria", 99, "FIJO", BigDecimal.ONE))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Categoría");
        assertThatThrownBy(() -> service.create("Tipo malo", 1, "VARIABLE", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipoCosto");
        assertThatThrownBy(() -> service.create("Valor negativo", 1, "FIJO", BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor");
        assertThatThrownBy(() -> service.create("Porcentaje alto", 1, "PORCENTAJE", BigDecimal.valueOf(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("porcentaje");
    }

    @Test
    void update_actualizaYRechazaDuplicadoDeOtroRegistro() {
        TypeDamageService service = service();
        TypeDamage existing = typeDamage(10, "Viejo", category(1, "Fisico"), "FIJO", "2.00");
        TypeDamage other = typeDamage(11, "Nuevo", category(1, "Fisico"), "FIJO", "3.00");
        given(typeDamageRepo.findById(10)).willReturn(Optional.of(existing));
        given(typeDamageRepo.findByName("Nuevo")).willReturn(Optional.empty(), Optional.of(other));
        given(categoryDamageRepo.findById(2)).willReturn(Optional.of(category(2, "Uso")));
        given(typeDamageRepo.save(any(TypeDamage.class))).willAnswer(inv -> inv.getArgument(0));

        TypeDamageDTO result = service.update(10, "Nuevo", 2, "PORCENTAJE", BigDecimal.TEN);

        assertThat(result.name()).isEqualTo("Nuevo");
        assertThat(result.categoryId()).isEqualTo(2);
        assertThat(result.typeCost()).isEqualTo("PORCENTAJE");
        assertThatThrownBy(() -> service.update(10, "Nuevo", 2, "FIJO", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro tipo");
    }

    @Test
    void delete_marcaInactivo() {
        TypeDamageService service = service();
        TypeDamage existing = typeDamage(7, "Ruptura", category(1, "Fisico"), "FIJO", "2.00");
        given(typeDamageRepo.findById(7)).willReturn(Optional.of(existing));

        service.delete(7);

        assertThat(existing.getActive()).isFalse();
        verify(typeDamageRepo).save(existing);
    }

    private TypeDamageService service() {
        return new TypeDamageService(typeDamageRepo, categoryDamageRepo);
    }

    private TypeDamage typeDamage(Integer id, String name, CategoryDamage category, String typeCost, String value) {
        TypeDamage type = new TypeDamage();
        type.setId(id);
        type.setName(name);
        type.setCategory(category);
        type.setTypeCost(typeCost);
        type.setValue(new BigDecimal(value));
        type.setActive(true);
        return type;
    }

    private CategoryDamage category(Integer id, String name) {
        CategoryDamage category = new CategoryDamage();
        category.setId(id);
        category.setName(name);
        return category;
    }
}

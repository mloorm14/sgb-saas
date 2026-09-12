package com.uteq.backend.controller;

import com.uteq.backend.dto.SupplierRequestDTO;
import com.uteq.backend.entity.Supplier;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "gerente@correo.com", roles = "GERENTE")
class SupplierControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierRepository supplierRepository;

    private Supplier supplier() {
        Supplier p = new Supplier();
        p.setId(1);
        p.setName("Editorial UTEQ");
        p.setRuc("1790012345001");
        p.setDireccion("Calle 1");
        p.setTelefono("099111222");
        p.setEmail("prov@correo.com");
        p.setPersonaContacto("Ana");
        p.setActive(true);
        return p;
    }

    private SupplierRequestDTO requestValid() {
        return new SupplierRequestDTO(
                "Editorial UTEQ", "1790012345001", "Calle 1",
                "099111222", "prov@correo.com", "Ana", true);
    }

    @Test
    void list_withoutFilters_devuelve200() throws Exception {
        when(supplierRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier())));

        mockMvc.perform(get("/api/v1/proveedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Editorial UTEQ"));
    }

    @Test
    void list_withFilters_devuelve200() throws Exception {
        when(supplierRepository.searchWithFilters(eq("uteq"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier())));

        mockMvc.perform(get("/api/v1/proveedores").param("q", "uteq").param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ruc").value("1790012345001"));
    }

    @Test
    void list_qBlanco_usaFindAll() throws Exception {
        when(supplierRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier())));

        mockMvc.perform(get("/api/v1/proveedores").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Editorial UTEQ"));
    }

    @Test
    void list_soloActive_usaSearchWithFilters() throws Exception {
        when(supplierRepository.searchWithFilters(eq(null), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(supplier())));

        mockMvc.perform(get("/api/v1/proveedores").param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ruc").value("1790012345001"));
    }

    @Test
    void listTodo_devuelve200() throws Exception {
        when(supplierRepository.findAll()).thenReturn(List.of(supplier()));

        mockMvc.perform(get("/api/v1/proveedores/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void search_devuelve200() throws Exception {
        when(supplierRepository.findTop5ByNameContainingIgnoreCase("uteq"))
                .thenReturn(List.of(supplier()));

        mockMvc.perform(get("/api/v1/proveedores/buscar").param("q", "uteq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("prov@correo.com"));
    }

    @Test
    void create_dataValids_devuelve201() throws Exception {
        when(supplierRepository.existsByNameIgnoreCase("Editorial UTEQ")).thenReturn(false);
        when(supplierRepository.existsByRucIgnoreCase("1790012345001")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenReturn(supplier());

        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_withoutName_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"1790012345001","direccion":"Calle 1",
                                "telefono":"099111222","email":"prov@correo.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_nameDuplicate_devuelve422() throws Exception {
        when(supplierRepository.existsByNameIgnoreCase("Editorial UTEQ")).thenReturn(true);

        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_rucDuplicate_devuelve422() throws Exception {
        when(supplierRepository.existsByNameIgnoreCase("Editorial UTEQ")).thenReturn(false);
        when(supplierRepository.existsByRucIgnoreCase("1790012345001")).thenReturn(true);

        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_existing_devuelve200() throws Exception {
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier()));
        when(supplierRepository.save(any(Supplier.class))).thenReturn(supplier());

        mockMvc.perform(put("/api/v1/proveedores/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Editorial UTEQ"));
    }

    @Test
    void update_inexistente_devuelve404() throws Exception {
        when(supplierRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/proveedores/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isNotFound());
    }
}

package com.uteq.backend.controller;

import com.uteq.backend.dto.ProveedorRequestDTO;
import com.uteq.backend.entity.Proveedor;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.ProveedorRepository;
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

@WebMvcTest(ProveedorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "gerente@correo.com", roles = "GERENTE")
class ProveedorControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProveedorRepository proveedorRepository;

    private Proveedor proveedor() {
        Proveedor p = new Proveedor();
        p.setId(1);
        p.setNombre("Editorial UTEQ");
        p.setRuc("1790012345001");
        p.setDireccion("Calle 1");
        p.setTelefono("099111222");
        p.setEmail("prov@correo.com");
        p.setPersonaContacto("Ana");
        p.setActivo(true);
        return p;
    }

    private ProveedorRequestDTO requestValido() {
        return new ProveedorRequestDTO(
                "Editorial UTEQ", "1790012345001", "Calle 1",
                "099111222", "prov@correo.com", "Ana", true);
    }

    @Test
    void listar_sinFiltros_devuelve200() throws Exception {
        when(proveedorRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proveedor())));

        mockMvc.perform(get("/api/v1/proveedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Editorial UTEQ"));
    }

    @Test
    void listar_conFiltros_devuelve200() throws Exception {
        when(proveedorRepository.buscarConFiltros(eq("uteq"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proveedor())));

        mockMvc.perform(get("/api/v1/proveedores").param("q", "uteq").param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ruc").value("1790012345001"));
    }

    @Test
    void listar_qEnBlanco_usaFindAll() throws Exception {
        when(proveedorRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proveedor())));

        mockMvc.perform(get("/api/v1/proveedores").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Editorial UTEQ"));
    }

    @Test
    void listar_soloActivo_usaBuscarConFiltros() throws Exception {
        when(proveedorRepository.buscarConFiltros(eq(null), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proveedor())));

        mockMvc.perform(get("/api/v1/proveedores").param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ruc").value("1790012345001"));
    }

    @Test
    void listarTodo_devuelve200() throws Exception {
        when(proveedorRepository.findAll()).thenReturn(List.of(proveedor()));

        mockMvc.perform(get("/api/v1/proveedores/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void buscar_devuelve200() throws Exception {
        when(proveedorRepository.findTop5ByNombreContainingIgnoreCase("uteq"))
                .thenReturn(List.of(proveedor()));

        mockMvc.perform(get("/api/v1/proveedores/buscar").param("q", "uteq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("prov@correo.com"));
    }

    @Test
    void crear_datosValidos_devuelve201() throws Exception {
        when(proveedorRepository.existsByNombreIgnoreCase("Editorial UTEQ")).thenReturn(false);
        when(proveedorRepository.existsByRucIgnoreCase("1790012345001")).thenReturn(false);
        when(proveedorRepository.save(any(Proveedor.class))).thenReturn(proveedor());

        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void crear_sinNombre_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruc":"1790012345001","direccion":"Calle 1",
                                "telefono":"099111222","email":"prov@correo.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_nombreDuplicado_devuelve422() throws Exception {
        when(proveedorRepository.existsByNombreIgnoreCase("Editorial UTEQ")).thenReturn(true);

        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_rucDuplicado_devuelve422() throws Exception {
        when(proveedorRepository.existsByNombreIgnoreCase("Editorial UTEQ")).thenReturn(false);
        when(proveedorRepository.existsByRucIgnoreCase("1790012345001")).thenReturn(true);

        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void actualizar_existente_devuelve200() throws Exception {
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor()));
        when(proveedorRepository.save(any(Proveedor.class))).thenReturn(proveedor());

        mockMvc.perform(put("/api/v1/proveedores/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Editorial UTEQ"));
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        when(proveedorRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/proveedores/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isNotFound());
    }
}

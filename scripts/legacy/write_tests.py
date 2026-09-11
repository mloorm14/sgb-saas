import os

base = r'backend-springboot\src\test\java\com\uteq\backend\controller'

def write(fname, content):
    path = os.path.join(base, fname)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(content)
    print('Written:', fname)

write('CredencialQrControllerTest.java', """package com.uteq.backend.controller;

import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.CredencialQrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CredencialQrController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "lector@correo.com", roles = "LECTOR")
class CredencialQrControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CredencialQrService credencialQrService;

    @Test
    void miCredencial_devuelve200ConImagen() throws Exception {
        byte[] pngMock = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        when(credencialQrService.generarImagenQrPropio(any())).thenReturn(pngMock);

        mockMvc.perform(get("/api/v1/credencial-qr/mi-credencial")
                        .principal(new TestingAuthenticationToken("lector@correo.com", null, "ROLE_LECTOR")))
                .andExpect(status().isOk());
    }
}
""")

write('ReservacionControllerTest.java', """package com.uteq.backend.controller;

import com.uteq.backend.dto.CambioEstadoReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionHoyResponseDTO;
import com.uteq.backend.dto.ReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.ReservacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservacionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class ReservacionControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservacionService reservacionService;

    private final TestingAuthenticationToken biblioAuth =
            new TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");

    @Test
    void crear_datosValidos_devuelve201() throws Exception {
        ReservacionResponseDTO resp = new ReservacionResponseDTO(1L, 2L, 3L, 1, OffsetDateTime.now(), OffsetDateTime.now().plusDays(3));
        when(reservacionService.crear(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/reservaciones")
                        .principal(biblioAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"usuarioId\\":2,\\"libroId\\":3,\\"fechaRetiro\\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void reservacionesDeHoy_devuelve200() throws Exception {
        ReservacionHoyResponseDTO dto = new ReservacionHoyResponseDTO(1L, "Pedro", "pedro@u.com", "Libro A", "978-0", "PENDIENTE", OffsetDateTime.now());
        when(reservacionService.buscarReservacionesDeHoy()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/reservaciones/hoy")
                        .principal(biblioAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservacionId").value(1));
    }

    @Test
    void reservacionesProximas_devuelve200() throws Exception {
        ReservacionHoyResponseDTO dto = new ReservacionHoyResponseDTO(2L, "Maria", "maria@u.com", "Libro B", "978-1", "PENDIENTE", OffsetDateTime.now().plusDays(2));
        when(reservacionService.buscarReservacionesProximas()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/reservaciones/proximas")
                        .principal(biblioAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservacionId").value(2));
    }

    @Test
    void cambiarEstado_listaParaRetiro_devuelve200() throws Exception {
        ReservacionResponseDTO resp = new ReservacionResponseDTO(5L, 2L, 3L, 2, OffsetDateTime.now(), OffsetDateTime.now().plusDays(3));
        when(reservacionService.cambiarEstado(eq(5L), any(CambioEstadoReservacionRequestDTO.class), any())).thenReturn(resp);

        mockMvc.perform(patch("/api/v1/reservaciones/5/estado")
                        .principal(biblioAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"nuevoEstado\\":\\"LISTA_PARA_RETIRO\\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void listarPorUsuario_devuelve200() throws Exception {
        Page<ReservacionResponseDTO> page = new PageImpl<>(List.of());
        when(reservacionService.listarPorUsuario(eq(2L), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/reservaciones/usuario/2")
                        .principal(biblioAuth))
                .andExpect(status().isOk());
    }
}
""")

write('EditorialControllerTest.java', """package com.uteq.backend.controller;

import com.uteq.backend.entity.Editorial;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.EditorialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EditorialController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EditorialControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EditorialRepository editorialRepository;

    private Editorial editorial(Long id, String nombre) {
        Editorial e = new Editorial();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    @Test
    void listar_devuelve200() throws Exception {
        when(editorialRepository.findAll()).thenReturn(List.of(editorial(1L, "Planeta")));

        mockMvc.perform(get("/api/v1/editoriales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Planeta"));
    }

    @Test
    void buscar_devuelve200() throws Exception {
        when(editorialRepository.findTop5ByNombreContainingIgnoreCase("Plan"))
                .thenReturn(List.of(editorial(1L, "Planeta")));

        mockMvc.perform(get("/api/v1/editoriales/buscar").param("q", "Plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Planeta"));
    }

    @Test
    void crear_nuevo_devuelve201() throws Exception {
        when(editorialRepository.existsByNombreIgnoreCase("Alfaguara")).thenReturn(false);
        Editorial guardada = editorial(5L, "Alfaguara");
        when(editorialRepository.save(any())).thenReturn(guardada);

        mockMvc.perform(post("/api/v1/editoriales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"nombre\\":\\"Alfaguara\\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void crear_duplicado_devuelve422() throws Exception {
        when(editorialRepository.existsByNombreIgnoreCase(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/editoriales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"nombre\\":\\"Planeta\\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
""")

write('AuditoriaControllerTest.java', """package com.uteq.backend.controller;

import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class AuditoriaControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @Test
    void listar_sinFiltros_devuelve200() throws Exception {
        Page page = new PageImpl(List.of());
        when(auditoriaService.listar(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    void resumen_devuelve200() throws Exception {
        when(auditoriaService.resumen()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auditoria/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void export_devuelve200() throws Exception {
        byte[] csv = "id,accion".getBytes();
        when(auditoriaService.exportarCsv(any(), any(), any(), any())).thenReturn(csv);

        mockMvc.perform(get("/api/v1/auditoria/export"))
                .andExpect(status().isOk());
    }
}
""")

write('ReservacionesGestionControllerTest.java', """package com.uteq.backend.controller;

import com.uteq.backend.dto.UsuarioReservacionesGestionDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.ReservacionesGestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservacionesGestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class ReservacionesGestionControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservacionesGestionService reservacionesGestionService;

    @Test
    void buscarUsuario_correoValido_devuelve200() throws Exception {
        UsuarioReservacionesGestionDTO dto = new UsuarioReservacionesGestionDTO(5L, "Maria", "maria@uteq.edu.ec", "LECTOR", 2L, 3);
        when(reservacionesGestionService.buscarPorCorreo("maria@uteq.edu.ec")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/reservaciones/gestion/buscar-usuario")
                        .param("correo", "maria@uteq.edu.ec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void historialReservaciones_devuelve200() throws Exception {
        when(reservacionesGestionService.historialReservaciones(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservaciones/gestion/historial-reservaciones")
                        .param("usuarioId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
""")

write('RespaldoCompletoControllerTest.java', """package com.uteq.backend.controller;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.RespaldoCompletoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RespaldoCompletoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class RespaldoCompletoControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RespaldoCompletoService service;

    private ConfiguracionRespaldo configMock() {
        ConfiguracionRespaldo c = new ConfiguracionRespaldo();
        c.setId(1L);
        c.setFrecuenciaHoras(24);
        c.setDiasRetencion(30);
        c.setHabilitado(true);
        return c;
    }

    private RegistroRespaldo registroMock() {
        RegistroRespaldo r = new RegistroRespaldo();
        r.setId(10L);
        r.setTipo("COMPLETO");
        r.setEstado("EXITOSO");
        r.setFechaInicio(OffsetDateTime.now());
        return r;
    }

    @Test
    void obtenerConfig_devuelve200() throws Exception {
        when(service.obtenerConfiguracion()).thenReturn(configMock());

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuenciaHoras").value(24));
    }

    @Test
    void actualizarConfig_devuelve200() throws Exception {
        when(service.actualizarConfiguracion(24, 30, true)).thenReturn(configMock());

        mockMvc.perform(put("/api/v1/admin/respaldo-completo/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"frecuenciaHoras\\":24,\\"diasRetencion\\":30,\\"habilitado\\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listarRegistros_sinTipo_devuelve200() throws Exception {
        when(service.listarTodos()).thenReturn(List.of(registroMock()));

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/registros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void listarRegistros_conTipo_devuelve200() throws Exception {
        when(service.listarPorTipo("COMPLETO")).thenReturn(List.of(registroMock()));

        mockMvc.perform(get("/api/v1/admin/respaldo-completo/registros").param("tipo", "COMPLETO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("COMPLETO"));
    }

    @Test
    void eliminarRegistro_devuelve204() throws Exception {
        doNothing().when(service).eliminar(10L);

        mockMvc.perform(delete("/api/v1/admin/respaldo-completo/registros/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void registrarInicio_devuelve200() throws Exception {
        when(service.registrarInicio("COMPLETO", null)).thenReturn(registroMock());

        mockMvc.perform(post("/api/v1/admin/respaldo-completo/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"tipo\\":\\"COMPLETO\\",\\"ejecutadoPor\\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("COMPLETO"));
    }

    @Test
    void registrarResultado_devuelve200() throws Exception {
        RegistroRespaldo r = registroMock();
        when(service.registrarResultado(eq(10L), any(), any(), any(), any(), any())).thenReturn(r);

        mockMvc.perform(put("/api/v1/admin/respaldo-completo/registros/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"estado\\":\\"EXITOSO\\",\\"nombreArchivo\\":\\"bk.zip\\",\\"tamanoArchivoBytes\\":1024,\\"rutaR2\\":null,\\"mensajeError\\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXITOSO"));
    }
}
""")

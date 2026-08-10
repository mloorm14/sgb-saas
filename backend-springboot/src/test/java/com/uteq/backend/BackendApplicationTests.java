package com.uteq.backend;

import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.ConfiguracionSistemaRepository;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.MultaProcedureRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.NotificacionRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.repository.ReservacionProcedureRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.RolRepository;
import com.uteq.backend.repository.TipoNotificacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
                "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration"
})
class BackendApplicationTests {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private LibroRepository libroRepository;

    @MockitoBean
    private EditorialRepository editorialRepository;

    @MockitoBean
    private IdiomaRepository idiomaRepository;

    @MockitoBean
    private EstadoLibroRepository estadoLibroRepository;

    @MockitoBean
    private RolRepository rolRepository;

    @MockitoBean
    private EstadoUsuarioRepository estadoUsuarioRepository;

    // ── Repositories del módulo Préstamos/Reservaciones/Multas ──
    // (agregados junto con feature/prestamos-backend; sin estos mocks el
    // contexto no carga porque DataJpaRepositoriesAutoConfiguration está
    // excluido arriba y no hay bean real para ningún JpaRepository/Repository).
    @MockitoBean
    private PrestamoRepository prestamoRepository;

    @MockitoBean
    private PrestamoProcedureRepository prestamoProcedureRepository;

    @MockitoBean
    private EstadoPrestamoRepository estadoPrestamoRepository;

    @MockitoBean
    private ReservacionRepository reservacionRepository;

    @MockitoBean
    private ReservacionProcedureRepository reservacionProcedureRepository;

    @MockitoBean
    private EstadoReservacionRepository estadoReservacionRepository;

    // Módulo 1 (renovación de préstamos): PrestamoService ahora también
    // depende de EstadoPrestamoRepository -- faltaba este mock en
    // feature/prestamos-avanzado, causaba
    // "No qualifying bean of type 'EstadoPrestamoRepository'" en
    // contextLoads() porque DataJpaRepositoriesAutoConfiguration sigue
    // excluido arriba (mismo motivo que el resto de mocks de esta clase).
    @MockitoBean
    private EstadoPrestamoRepository estadoPrestamoRepository;

    @MockitoBean
    private MultaRepository multaRepository;

    @MockitoBean
    private MultaProcedureRepository multaProcedureRepository;

    // OWASP A09 (Bloque C.2): AuthService.registrarAuditoria() ahora
    // depende de este repositorio -- mismo motivo que el resto de mocks de
    // esta clase, DataJpaRepositoriesAutoConfiguration sigue excluido.
    @MockitoBean
    private BitacoraAuditoriaRepository bitacoraAuditoriaRepository;

    @MockitoBean
    private ConfiguracionSistemaRepository configuracionSistemaRepository;

    // Módulo 2 (notificaciones): NotificacionService depende de estos dos
    // -- mismo motivo que el resto de mocks de esta clase
    // (DataJpaRepositoriesAutoConfiguration sigue excluido arriba, así que
    // cualquier JpaRepository nuevo sin @MockitoBean rompe contextLoads()
    // con "No qualifying bean of type ...").
    @MockitoBean
    private NotificacionRepository notificacionRepository;

    @MockitoBean
    private TipoNotificacionRepository tipoNotificacionRepository;

    @Test
    void contextLoads() {
    }

}
---
name: sgb-testing
description: Patrones de testing para backend (JUnit 5, Mockito, Testcontainers, H2) y frontend (Jasmine/Karma, Angular TestBed) en SGB-SaaS. Usar SIEMPRE al crear o editar archivos de test (*.spec.ts, *Test.java) — cubre estructura de tests, setup de mocks, patrones de assertions, y cómo correr tests individuales vs suite completa.
---

# Testing en SGB-SaaS

## Backend: JUnit 5 + Spring Boot Test

### Framework y dependencias

- JUnit 5 via `spring-boot-starter-test` (incluye Mockito)
- H2 in-memory para tests unitarios (schema automático por JPA)
- Testcontainers PostgreSQL 1.20.5 para integration tests que usan stored procedures PL/pgSQL
- Spring Security Test (`@WithMockUser`, `SecurityMockMvcRequestPostProcessors`)

### Estructura de un test de service

```java
@ExtendWith(MockitoExtension.class)
class LibroServiceTest {

    @Mock private LibroRepository libroRepo;
    @Mock private BitacoraAuditoriaRepository bitacoraRepo;
    @InjectMocks private LibroService libroService;

    @Test
    @DisplayName("deberia crear libro cuando datos son validos")
    void deberiaCrearLibro() {
        // Arrange
        LibroDto dto = new LibroDto("ISBN-123", "Titulo", 1L, 1L, 1L);
        when(libroRepo.save(any(Libro.class))).thenAnswer(inv -> {
            Libro l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        // Act
        Libro resultado = libroService.crear(dto);

        // Assert
        assertNotNull(resultado.getId());
        verify(libroRepo).save(any(Libro.class));
        verify(bitacoraRepo).save(any(BitacoraAuditoria.class));
    }

    @Test
    @DisplayName("deberia lanzar excepcion cuando ISBN ya existe")
    void deberiaLanzarExcepcionSiISBNExiste() {
        when(libroRepo.existsByIsbn("ISBN-123")).thenReturn(true);
        assertThrows(ConflictException.class,
            () -> libroService.crear(new LibroDto("ISBN-123", "...", 1L, 1L, 1L)));
    }
}
```

### Estructura de un test de controller (@WebMvcTest)

```java
@WebMvcTest(LibroController.class)
class LibroControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private LibroService libroService;
    @MockBean private BitacoraAuditoriaRepository bitacoraRepo;

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    @DisplayName("deberia retornar 200 al listar libros")
    void deberiaListarLibros() throws Exception {
        when(libroService.listar(any())).thenReturn(List.of(/* ... */));
        mockMvc.perform(get("/api/v1/libros"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deberia retornar 401 sin autenticacion")
    void deberiaRechazarSinAuth() throws Exception {
        mockMvc.perform(get("/api/v1/libros"))
            .andExpect(status().isUnauthorized());
    }
}
```

### Integration tests con Testcontainers

Solo usar cuando se necesita PL/pgSQL stored procedures (no funcionan con H2):

```java
@SpringBootTest
@Testcontainers
class PrestamoMultaProcedureTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("sgb_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema-init.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void deberiaCalcularMultaPorDiasRetraso() {
        // ... usar stored procedure real
    }
}
```

### Cobertura (JaCoCo)

- Excluidos: `dto/`, `entity/`, `config/`, `BackendApplication`
- Reporte en `backend-springboot/target/site/jacoco/`
- Run: `./mvnw verify` (incluye JaCoCo + SpotBugs)

## Frontend: Jasmine/Karma

### Estructura de un test de componente

```typescript
describe('CatalogoComponent', () => {
  let component: CatalogoComponent;
  let fixture: ComponentFixture<CatalogoComponent>;
  let libroServiceSpy: jasmine.SpyObj<LibroService>;

  beforeEach(async () => {
    const spy = jasmine.createSpyObj('LibroService', ['listar', 'obtenerPortada']);
    spy.listar.and.returnValue(of({ content: [], totalElements: 0 }));

    await TestBed.configureTestingModule({
      imports: [CatalogoComponent, HttpClientTestingModule, ReactiveFormsModule],
      providers: [{ provide: LibroService, useValue: spy }]
    }).compileComponents();

    libroServiceSpy = TestBed.inject(LibroService) as jasmine.SpyObj<LibroService>;
    fixture = TestBed.createComponent(CatalogoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load books on init', () => {
    expect(libroServiceSpy.listar).toHaveBeenCalled();
  });
});
```

### Estructura de un test de service

```typescript
describe('LibroService', () => {
  let service: LibroService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [LibroService]
    });
    service = TestBed.inject(LibroService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should fetch books', () => {
    service.listar({ page: 0, size: 10 }).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/libros?page=0&size=10`);
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0 });
  });
});
```

### Errores con ProblemDetail

Los services usan `catchError` + `ProblemDetail` (RFC 7807). En tests, simular errores así:

```typescript
it('should handle error when fetching books', () => {
  service.listar({ page: 0, size: 10 }).subscribe({
    error: (err) => {
      expect(err.status).toBe(404);
    }
  });
  const req = httpMock.expectOne(/* ... */);
  req.flush('Not Found', { status: 404, statusText: 'Not Found' });
});
```

## Comandos de testing

### Backend

```bash
# Un solo clase (mientras iterás)
cd backend-springboot && ./mvnw test -Dtest=LibroServiceTest

# Suite completa (una vez al final de la rama)
cd backend-springboot && ./mvnw -B clean verify
```

### Frontend

```bash
# Un solo archivo (mientras iterás)
cd frontend-angular && npx ng test --include='**/catalogo.component.spec.ts' --watch=false

# Suite completa (una vez al final de la rama)
cd frontend-angular && npx ng test --watch=false --browsers=ChromeHeadless
```

### Makefile

```bash
make test-backend   # ./mvnw -B clean verify
make test-frontend  # npx ng test --watch=false --browsers=ChromeHeadless
make test           # ambos
```

## Reglas de testing

- Camino feliz + 1-2 casos de error reales por componente/clase nueva.
- NUNCA un test por cada combinación posible.
- Cada test debe ser independiente — no dependa de orden de ejecución.
- Mockear solo lo necesario — no hagas mock de la base de datos H2 en tests de service.
- Al reportar el TOTAL de tests al final de una tarea, reportá el número EXACTo, no aproximado.
- Si un test flaky falla, documentalo pero no lo borres — reportalo al equipo.

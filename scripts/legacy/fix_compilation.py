import os

base = 'backend-springboot/src/test/java/com/uteq/backend/controller/'

# AuthControllerTest
with open(base + 'AuthControllerTest.java', 'r', encoding='utf-8') as f:
    auth_content = f.read()

# Fix UsuarioResponseDTO instantiation
auth_content = auth_content.replace('new com.uteq.backend.dto.UsuarioResponseDTO(1L, "Juan", "Perez", "test@correo.com", "LECTOR", "ACTIVO")', 'new com.uteq.backend.dto.UsuarioResponseDTO(1L, "Juan", "Perez", java.util.List.of("LECTOR"))')

with open(base + 'AuthControllerTest.java', 'w', encoding='utf-8', newline='\n') as f:
    f.write(auth_content)


# LibroControllerTest
with open(base + 'LibroControllerTest.java', 'r', encoding='utf-8') as f:
    libro_content = f.read()

# Fix LibroResponseDTO (can just use mock or pass nulls, better to mock)
libro_content = libro_content.replace('new LibroResponseDTO()', 'org.mockito.Mockito.mock(LibroResponseDTO.class)')

with open(base + 'LibroControllerTest.java', 'w', encoding='utf-8', newline='\n') as f:
    f.write(libro_content)


# SugerenciaAdquisicionControllerSecurityTest
path = base + 'SugerenciaAdquisicionControllerSecurityTest.java'
with open(path, 'r', encoding='utf-8') as f:
    sug_content = f.read()

sug_content = sug_content.replace('when(sugerenciaAdquisicionService.listarPropias', 'when(sugerenciaService.listarPropias')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(sug_content)

print("Fixed")

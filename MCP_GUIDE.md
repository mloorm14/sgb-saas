# 🛠️ Guía del Entorno MCP: Graphify y Playwright (OpenCode Desktop)

Esta guía contiene la configuración, comandos de mantenimiento, verificación de salud y prompts de uso diario para trabajar con los servidores **Model Context Protocol (MCP)** en este proyecto.

> Norma vigente: `AGENTS.md` es la referencia obligatoria — navegación por grafo antes de `read`/`grep` masivo, y Playwright solo bajo demanda explícita.

---

## 🚀 1. Configuración Inicial (Solo la primera vez)

Si acabas de clonar el repositorio o cambiar a la rama de trabajo, ejecuta estos comandos en tu terminal local:

1. **Sincronizar el repositorio:**
   ```bash
   git pull
   ```

2. **Instalar el parser del grafo (con soporte SQL):**
   ```bash
   pip install "graphifyy[sql]"
   ```
   *(El paquete PyPI se llama `graphifyy` con doble "y"; el comando CLI sigue siendo `graphify`. Usá comillas dobles en Windows PowerShell.)*

3. **Generar la base de datos del grafo local:**
   ```bash
   graphify extract . --code-only --no-cluster
   ```

   *(Crea la carpeta `graphify-out/` de ~10 MB. Está ignorada en Git, no intentes incluirla en commits).*

4. **Reiniciar OpenCode Desktop:**
   Cierra completamente la aplicación y vuélvela a abrir para que cargue los servidores MCP registrados en `opencode.json`.

---

## 📊 2. Graphify MCP (Navegación por Grafo AST)

Evita la lectura masiva de archivos (`read_file`/`grep`) consultando un mapa de dependencias estáticas de Angular, Spring Boot y SQL, reduciendo drásticamente el consumo de tokens.

### 🔄 ¿Cuándo y cómo actualizar el grafo?

El grafo es una captura estática de la arquitectura. **Debes actualizarlo siempre que el código cambie**:

* **Tras hacer `git pull`:** Para registrar los cambios hechos por el equipo.
* **Tras crear/modificar clases o módulos:** Para que el agente reconozca los nuevos métodos e `imports`.

**Comandos de actualización:**

* **Desde la terminal (Recomendado - tarda segundos):**
  ```bash
  graphify update .
  ```

* **Desde el chat de OpenCode Desktop:**
  > *"Acabo de cambiar código / hacer pull. Actualiza el grafo local ejecutando `graphify update .`"*

*(Si hiciste refactorizaciones masivas o cambios estructurales de carpetas, regenera completo con: `graphify extract . --code-only --no-cluster`).*

### 🧪 Prueba de Verificación (Graphify)

Pega este prompt en una nueva sesión para confirmar que responde:

> *"Verifica la conexión con Graphify consultando la herramienta MCP para mostrar las dependencias directas de AuthService sin realizar lecturas de archivos ni búsquedas con grep."*

*(Nota: `AuthService` existe dos veces en este repo — backend `backend-springboot/src/main/java/com/uteq/backend/service/AuthService.java` y frontend `frontend-angular/src/app/core/services/auth.service.ts`. Si el grafo responde "Ambiguous", reintentá con el path repo-relativo del que te interese.)*

---

## 🎭 3. Playwright MCP (Pruebas E2E y QA en Render)

Permite al agente abrir un navegador en segundo plano e interactuar directamente con el entorno desplegado en Render para validar flujos y pantallas en tiempo real.

### ⚠️ Reglas y Tolerancia a Cold-Start (Render)

* **100% Automático:** No requiere instalaciones manuales. Se ejecuta vía `npx` en segundo plano al abrir OpenCode Desktop (requiere Node.js en la máquina).
* **Entorno Render:** La aplicación se prueba sobre la URL del entorno desplegado.
* **Tolerancia de 60 segundos:** Las instancias gratuitas de Render se suspenden por inactividad. El agente esperará hasta 60s en la primera carga para permitir que el servidor despierte. *(Aplica solo a Render, no a páginas locales como `about:blank`, que responden al instante.)*
* **Uso bajo demanda:** Playwright solo se activará si solicitas explícitamente probar la interfaz o un flujo E2E.

### 🧪 Prueba de Verificación (Playwright)

Pega este prompt en el chat para verificar que el navegador inicia localmente (sin consumir el servidor en vivo):

> *"Lista las herramientas disponibles de Playwright y abre la página local `about:blank` para comprobar que el motor del navegador inicia correctamente."*

---

## 📌 4. Tabla de Prompts Frecuentes

| Tarea | Prompt sugerido para el Chat |
| --- | --- |
| **Actualizar mapa AST** | *"Actualiza el grafo local ejecutando `graphify update .`"* |
| **Impacto de cambios** | *"Usa Graphify y dime qué archivos se ven afectados si modifico `UsuarioAdminService`."* |
| **Ruta entre capas** | *"Traza la ruta de dependencias entre `RegistroComponent` y `AuthService` usando el grafo."* |
| **Prueba visual en Render** | *"Usa Playwright para abrir la URL de Render y verifica si el módulo de proveedores carga correctamente."* |

---

## 🧰 5. Troubleshooting (Corto)

* **El grafo muestra vista agregada / "community nodes":** normal cuando supera ~5000 nodos; usá `query` / `path` / `explain` para subgrafos enfocados.
* **`graphify-out/` aparece como modificado tras hooks o updates:** esperado, no es motivo para saltear el grafo; tampoco se commitea (está ignorado en Git).
* **El MCP no aparece en OpenCode Desktop:** cerrá y reabrí la app para que recargue `opencode.json`; verificá que ambos servidores estén en `"enabled": true`.
* **Playwright: "Executable doesn't exist" (versión del browser):** el `playwright-core` instalado pide una build distinta a la descargada — fijá `executablePath` al binario existente en `%USERPROFILE%\AppData\Local\ms-playwright\` o corré `npx playwright install`.
* **Priorizá árbol de accesibilidad/DOM sobre screenshots** en Playwright para ahorrar contexto, salvo error visual.

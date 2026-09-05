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

### 🌐 Opciones de Visualización Interactiva (`graph.html`)

Puedes explorar el mapa visual de la arquitectura en `graphify-out/graph.html` en dos modalidades mediante estos prompts de un solo paso:

1. **🧩 Vista Normal / Agregada (Recomendada - Agrupada por Comunidades):**
   * **Descripción:** Extrae, calcula las comunidades y abre la interfaz visual sin saturar la pantalla.
   * **Prompt directo:**
     > *"Actualiza el grafo en vista agregada y ábrelo en mi navegador ejecutando en la terminal: `graphify extract . --code-only`, luego `graphify cluster-only .` y finalmente `start graphify-out/graph.html`."*

2. **🌌 Vista Plana Completa (Todos los nodos individuales):**
   * **Descripción:** Fuerza el renderizado punto por punto de todos los nodos del repositorio.
   * **Prompt directo:**
     > *"Genera la vista plana completa del grafo y ábrela en mi navegador ejecutando en la terminal: `graphify extract . --code-only --no-cluster`, luego `graphify export html --node-limit 10000` y finalmente `start graphify-out/graph.html`."*

> 💡 **Nota sobre el layout visual:** `graph.html` utiliza una simulación de fuerzas (D3.js). La posición visual de los nodos/burbujas puede cambiar ligeramente en cada recarga, pero la estructura de conexiones y dependencias del código es exactamente la misma.

---

## 🎭 3. Playwright MCP (Pruebas E2E y QA en Render)

Permite al agente operar un navegador Chromium real contra el entorno desplegado en Render (o cualquier entorno activo) para validar flujos y pantallas, en segundo plano o de forma visible en tu pantalla.

### ⚠️ Reglas y Tolerancia a Cold-Start (Render)

* **100% Automático:** No requiere instalaciones manuales. Se ejecuta vía `npx` en segundo plano al abrir OpenCode Desktop (requiere Node.js en la máquina).
* **Entorno desplegado:** Las pruebas E2E/QA están destinadas a la URL desplegada en Render, una vez activo el deploy (también sirve cualquier otro entorno activo que indiques en el prompt).
* **Tolerancia de 60 segundos:** Las instancias gratuitas de Render se suspenden por inactividad. El agente esperará hasta 60s **en la primera carga** para permitir que el servidor despierte. *(Aplica solo a Render, no a páginas locales como `about:blank`, que responden al instante.)*
* **Uso bajo demanda:** Playwright solo se activará si solicitas explícitamente probar la interfaz o un flujo E2E.

### 🎛️ Las 3 Modalidades de Ejecución de Pruebas

* **Modo 1: Silencioso / Headless (Por defecto — Recomendado para QA rápido)**
  - *Comportamiento:* Corre 100% en segundo plano sin abrir ventanas flotantes. Analiza DOM, red, respuestas API y consola a máxima velocidad.
  - *Ideal para:* Validar navegación, detectar errores HTTP (400/500) y comprobar respuestas de backend sin interrupción visual.
  - *Ejemplo de prompt:* *"Usa Playwright en segundo plano para verificar el flujo de..."*

* **Modo 2: Evidencia Visual (Con Screenshots)**
  - *Comportamiento:* Corre en segundo plano pero genera capturas de pantalla (`screenshot`) en pasos específicos o cuando detecta una falla.
  - *Ideal para:* Validar maquetación, diseño responsive y guardar evidencia del estado de las pantallas.
  - *Resguardo (costo de tokens):* screenshots solo a pedido o ante falla visual; por defecto se prioriza árbol de accesibilidad/DOM.
  - *Ejemplo de prompt:* *"Ejecuta Playwright en `[URL]` y toma una captura de pantalla antes y después de hacer clic en..."*

* **Modo 3: Visible / Interactivo (`headless: false`)**
  - *Comportamiento:* Abre la ventana del navegador Chromium en tu pantalla para observar en vivo al agente escribir, hacer clics y desplegar modales.
  - *Ideal para:* Depurar UX, verificar animaciones, probar modales de confirmación o acciones críticas (ej. bloquear/eliminar).
  - *Regla de seguridad en prod:* abrir los modales de acciones críticas pero **nunca confirmarlas** sin autorización explícita; usar filas de prueba secundarias, jamás el admin principal.

### 📝 Plantilla Estándar para Pruebas Visibles

Copia, adapta los `[corchetes]` y pégala en el chat para probar cualquier módulo o flujo:

```text
Usa Playwright MCP para probar el módulo de [Nombre del Módulo] en [Nombre de la App] con el navegador VISIBLE (`headless: false`):

1. **Navegador Visible:** Configura Playwright en modo gráfico (`headless: false`).
2. **URL Objetivo:** `[URL de Render o entorno desplegado]`
3. **Autenticación / Requisitos Previos:**
   - Navega a `/login`.
   - Completa los pasos previos (ej. marcar checkbox de políticas si aplica).
   - Inicia sesión con las credenciales de prueba `[usuario / clave]` (ver cuentas de prueba en `README.md`).
4. **Prueba Específica del Módulo:**
   - Navega a la vista/sección `[Nombre de la Sección]`.
   - Ubica el elemento o fila a probar `[ej. usuario secundario / registro específico]`.
   - **Prueba de Acción 1:** Haz clic en `[ej. Botón de Filtro / Estado / Bloquear]` y verifica la respuesta.
   - **Prueba de Acción 2:** Haz clic en `[ej. Botón Eliminar / Editar]` y confirma si despliega el modal de confirmación o notificación.
5. **Cierre y Reporte:** Mantén la ventana abierta unos segundos para apreciar la interacción y reporta en el chat si los componentes respondieron, si el backend arrojó errores de consola/HTTP, y confirma explícitamente que nada fue mutado.
```

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
| **QA visible de módulo** | *"Ejecuta un QA visible de [Módulo] en Render sin confirmar acciones destructivas."* |
| **Ver mapa visual interactivo del grafo** | `start graphify-out/graph.html` (o pedirle al agente: *"Actualiza el grafo y dame la ruta absoluta de graph.html para abrirlo"*). |

---

## 🧰 5. Troubleshooting (Corto)

* **El grafo muestra vista agregada / "community nodes":** normal cuando supera ~5000 nodos; usá `query` / `path` / `explain` para subgrafos enfocados.
* **`graphify-out/` aparece como modificado tras hooks o updates:** esperado, no es motivo para saltear el grafo; tampoco se commitea (está ignorado en Git).
* **El MCP no aparece en OpenCode Desktop:** cerrá y reabrí la app para que recargue `opencode.json`; verificá que ambos servidores estén en `"enabled": true`.
* **Playwright: "Executable doesn't exist" (versión del browser):** el `playwright-core` instalado pide una build distinta a la descargada — fijá `executablePath` al binario existente en `%USERPROFILE%\AppData\Local\ms-playwright\` o corré `npx playwright install`.
* **Priorizá árbol de accesibilidad/DOM sobre screenshots** en Playwright para ahorrar contexto, salvo error visual.

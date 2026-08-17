.PHONY: up down test test-backend test-frontend bench audit docs clean all check-latexmk

# make up: regenera db/init/01-consolidado.sql (schema + procs + seed, ver
# scripts/build-init-sql.sh) y levanta todos los servicios (Postgres, Redis,
# backend, frontend) reconstruyendo las imagenes si hay cambios, esperando
# a que todos los contenedores con healthcheck definido reporten estado
# "healthy" antes de devolver el control (timeout de ~60s).
up:
	bash scripts/build-init-sql.sh
	docker compose up -d --build
	@echo "Esperando healthchecks..."
	@i=0; \
	while docker compose ps | grep -qE "starting|unhealthy"; do \
		i=$$((i+1)); \
		if [ $$i -gt 30 ]; then echo "Timeout esperando healthchecks"; exit 1; fi; \
		sleep 2; \
	done
	@echo "Todos los servicios estan arriba."

# make down: detiene y elimina los contenedores, conservando los volumenes
# de datos (pgdata) para no perder informacion entre reinicios.
down:
	docker compose down

# make test: ejecuta la suite completa (backend + frontend). Delega en los
# dos targets de abajo para que correr solo uno de los dos (mientras se
# itera en un cambio que toca un solo lado) no obligue a esperar al otro.
test: test-backend test-frontend

# make test-backend: Maven, incluye tests de integracion via 'verify'.
test-backend:
	cd backend-springboot && ./mvnw -B clean verify

# make test-frontend: Angular, modo single-run sin watch y navegador
# headless -- mismo comando que corre .github/workflows/ci.yml (job
# "frontend"), para que si pasa en la maquina de alguien tambien pase en
# GitHub Actions.
#
# El guard de node_modules es para el clone limpio: 'ng test' sin
# dependencias instaladas falla con un error criptico de modulo ausente, y
# el criterio R1/D.1 exige que 'make all' corra de punta a punta desde una
# carpeta nueva (npm ci es exactamente el mismo paso que CI ya ejecuta
# antes de 'npx ng test').
#
# El guard compara mtimes en vez de solo chequear que node_modules exista:
# un node_modules cacheado de ANTES de un cambio real de dependencias
# (ver el upgrade a Angular 21.2.20 mergeado hoy) queda desactualizado sin
# desaparecer, y 'ng test' contra un Angular viejo falla con errores
# crípticos de compilador (NG8001/'add an @NgModule annotation') en vez de
# reinstalar solo. node_modules/.package-lock.json es el marker que 'npm
# ci'/'npm install' actualizan en cada corrida (confirmado contra la
# version de npm de este entorno, no asumido) -- si falta (node_modules
# nunca se instalo con npm, o se borro a mano) o package-lock.json es mas
# nuevo que el marker (cambio de dependencias sin reinstalar), se corre
# 'npm ci'; si el marker es igual o mas nuevo, se saltea. '-nt' es
# soportado por el /bin/sh que este Makefile ya asume en otros targets
# (confirmado en este entorno), no es un bashismo.
#
# No exporta CHROME_BIN a mano: se probo en una maquina Windows limpia
# (sin Chrome instalado) que karma-chrome-launcher busca por defecto en
# C:\Program Files\Google\Chrome\Application\chrome.exe y falla si no esta
# -- eso rompia D.1/R1 tambien. frontend-angular/karma.conf.js (ver
# angular.json -> test.options.karmaConfig) resuelve CHROME_BIN solo, en
# este orden: 1) si ya esta seteado, lo respeta; 2) un Chrome/Edge/Chromium
# ya instalado en la maquina (cubre la gran mayoria de casos reales); 3)
# como ultimo recurso, el Chromium que instala 'puppeteer' (devDependency).
# Ese ultimo paso ya NO se baja solo con 'npm install'/'npm ci' (ver
# frontend-angular/.puppeteerrc.cjs): se confirmo un 403 real
# descargandolo en una red con lista blanca de dominios, lo que hacia
# fallar el 'npm ci' completo -- ver docs/despliegue/DEPLOYMENT.md,
# seccion 10, para el detalle.
test-frontend:
	cd frontend-angular && ([ -d node_modules ] && [ node_modules/.package-lock.json -nt package-lock.json ] || npm ci)
	cd frontend-angular && npx ng test --watch=false --browsers=ChromeHeadless

# make bench: Bloque C.1 -- corre k6/libros-listado-test.js (cache_caliente +
# cache_frio, GET /api/v1/libros) contra el stack real, reproduciendo el
# comando documentado en docs/mediciones/perf/REPORT.md. Levanta el stack si
# hace falta, espera healthchecks (mismo patron que 'up'), guarda el JSON
# crudo de k6 en docs/mediciones/perf/ con el siguiente numero de run
# disponible (nunca sobrescribe corridas previas, ver
# docs/mediciones/README.md) e imprime un resumen p50/p95 con
# scripts/perf-analysis.py. NO genera el .md de analisis -- eso es un paso
# manual de interpretacion.
#
# FIX (descubierto en la verificacion de 'make all' desde clone limpio en
# Windows): el shell que usa make (Git Bash sh.exe) resuelve $(pwd) a un
# path MSYS-aliasado (/tmp/... = %TEMP%) cuando PWD no viene en el
# entorno, y Docker Desktop con MSYS_NO_PATHCONV=1 lo interpreta como
# C:\tmp\... -- un directorio NUEVO y vacio que Docker crea en silencio.
# Resultado: k6 corre contra un volumen vacio y no encuentra
# /scripts/libros-listado-test.js. En Linux esto nunca se nota (pwd es el
# path real). Se normaliza el path del host con cygpath -m cuando existe
# (Git for Windows) y se cae a pwd en Linux/macOS -- comportamiento
# identico al original en los sistemas donde ya funcionaba.
bench:
	docker compose up -d
	@echo "Esperando healthchecks..."
	@i=0; \
	while docker compose ps | grep -qE "starting|unhealthy"; do \
		i=$$((i+1)); \
		if [ $$i -gt 30 ]; then echo "Timeout esperando healthchecks"; exit 1; fi; \
		sleep 2; \
	done
	@echo "Todos los servicios estan arriba."
	@host=$$(command -v cygpath >/dev/null 2>&1 && cygpath -m "$$(pwd)" || printf '%s' "$$(pwd)"); \
	last=$$(ls docs/mediciones/perf/k6-run*.json 2>/dev/null | sed -E 's/.*k6-run([0-9]+)\.json/\1/' | sort -n | tail -1); \
	next=$$(( $${last:-0} + 1 )); \
	echo "Corrida k6 -> docs/mediciones/perf/k6-run$$next.json"; \
	MSYS_NO_PATHCONV=1 docker run --rm --network sgb-saas_default \
		-v "$$host/k6:/scripts" -v "$$host/docs/mediciones/perf:/out" \
		grafana/k6 run --out json=/out/k6-run$$next.json /scripts/libros-listado-test.js; \
	echo ""; \
	echo "Resumen (p50/p95) de esta corrida:"; \
	python3 scripts/perf-analysis.py docs/mediciones/perf/k6-run$$next.json | grep -E '"escenario"|"n_peticiones"|"p50_ms"|"p95_ms"'

# make audit: Bloque C.2 -- re-verificacion automatizada de los controles
# de seguridad contra el stack Docker real. Genera un .md nuevo en
# docs/mediciones/sec/ sin tocar los archivos de evidencia originales.
#
# Orden deliberado: primero scripts/audit-sql-dynamic.sh (A.2.3, SQL
# dinamico por concatenacion en db/procs/ -- el hallazgo mas grave) y solo
# si ese pasa, scripts/owasp-audit.sh (A01/A03/A07/A09; A02/A05 quedan
# fuera, ver comentario en el propio script). Replica el mismo gate que ya
# hace CI (ver .github/workflows/ci.yml) para que 'make all' local no
# pueda dar verde saltandose el chequeo de SQL dinamico -- antes de este
# target, 'make all' nunca corria ese script, solo CI lo hacia. El orden
# de severidad tambien importa: si hay SQL inyectable no tiene sentido
# gastar el tiempo de correr el resto de los controles.
audit:
	bash scripts/audit-sql-dynamic.sh
	bash scripts/owasp-audit.sh

# make docs: Bloque D.1/D.2 -- regenera la evidencia documental que SI
# cambia con cada corrida y verifica la que no cambia:
#   1. docs/entorno/versions.txt (scripts/capture-versions.sh): versiones
#      reales del entorno (D.2, "actualizado con cada release").
#   2. Analisis estadistico de rendimiento (scripts/perf-analysis.py) sobre
#      TODAS las corridas archivadas k6-run*.json: Wilcoxon pareado +
#      Cliff's delta + grafico SVG p95. NO reimplementa nada de la logica,
#      solo la invoca -- las figuras de rendimiento SI cambian con cada
#      corrida de k6, por eso se regeneran.
#   3. Sincronia del render C4 vs workspace.dsl: unicamente una ADVERTENCIA
#      si el .dsl es mas reciente que los .svg/.png archivados. NO se
#      regenera el render en cada corrida: el modelo C4 es estatico y la
#      regeneracion exige Docker + Structurizr (imagen structurizr/structurizr,
#      ver nota de deprecacion de structurizr/cli en el propio workspace.dsl)
#      -- agregarla aqui solo anadiria una dependencia fragil a un target
#      que debe ser confiable de punta a punta. Si hace falta regenerar,
#      hacerlo manualmente siguiendo los pasos documentados en workspace.dsl.
#   4. Existencia del informe LaTeX: aviso claro (no error) si existe
#      informe-entrega-3.tex pero no informe-final.tex -- probable rename
#      pendiente del equipo de documentacion. NO compila el PDF: eso es de
#      'make all'.
# El target termina en exit code != 0 si cualquiera de los pasos 1-2
# falla (ningun paso se envuelve en '|| true' -- un verde falso es peor
# que un rojo honesto).
docs:
	bash scripts/capture-versions.sh
	python3 scripts/perf-analysis.py docs/mediciones/perf/k6-run*.json
	@echo "Verificando sincronia del render C4 vs workspace.dsl..."
	@dsl=docs/arquitectura/workspace.dsl; \
	for f in docs/arquitectura/*.svg docs/arquitectura/*.png; do \
		[ -f "$$f" ] || continue; \
		dsl_t=$$(stat -c %Y "$$dsl"); \
		f_t=$$(stat -c %Y "$$f"); \
		if [ "$$dsl_t" -gt "$$f_t" ]; then \
			echo "AVISO: $$f es mas antiguo que $$dsl -- el diagrama archivado podria estar"; \
			echo "       desactualizado. Regenerarlo a mano si el modelo C4 cambio (pasos en"; \
			echo "       docs/arquitectura/workspace.dsl). No bloquea 'make docs'."; \
		else \
			echo "OK: $$f no es mas antiguo que $$dsl."; \
		fi; \
	done
	@if [ -f docs/informe-final.tex ]; then \
		echo "OK: informe LaTeX encontrado (docs/informe-final.tex)."; \
	elif [ -f docs/informe-entrega-3.tex ]; then \
		echo "AVISO: no existe docs/informe-final.tex, pero si docs/informe-entrega-3.tex."; \
		echo "       Probablemente falta el rename a informe-final.tex por parte del equipo de"; \
		echo "       documentacion antes del cierre. No bloquea 'make docs' (el PDF se compila"; \
		echo "       en 'make all')."; \
	else \
		echo "AVISO: no se encontro ningun .tex de informe (ni informe-final.tex ni"; \
		echo "       informe-entrega-3.tex) en docs/ -- ver equipo de documentacion."; \
	fi
	@echo "make docs completado."

# check-latexmk: verificacion temprana de que latexmk existe en este
# entorno, antes de gastar tiempo en 'up'/'test'/'bench'/'audit'/'docs'
# (que pueden tardar varios minutos) solo para fallar al final compilando
# el PDF. Falla con un mensaje claro en vez del error generico "command
# not found" que daria Make si se llamara a latexmk directamente sin este
# chequeo. No instala nada -- instalar una distribucion TeX (MiKTeX, TeX
# Live, etc.) es una decision del entorno de quien corre esto, fuera del
# alcance de este Makefile.
check-latexmk:
	@command -v latexmk >/dev/null 2>&1 || { \
		echo "ERROR: latexmk no esta instalado en este entorno."; \
		echo "'make all' necesita compilar el informe final en PDF (requisito D.1)."; \
		echo "Instalarlo: Windows -> MiKTeX (winget install MiKTeX.MiKTeX);"; \
		echo "Debian/Ubuntu -> apt install latexmk; macOS -> brew install latexmk."; \
		exit 1; \
	}

# make all: pipeline reproducible de punta a punta (criterio R1 / D.1) --
# falla temprano si falta latexmk (check-latexmk), levanta el stack en
# limpio (up), corre la suite de tests (test), la prueba de carga real
# (bench), las auditorias (audit), regenera la evidencia documental
# (docs) y compila el PDF final del informe LaTeX. Ningun paso se declara
# '|| true': si cualquiera falla, make all falla (un verde falso es peor
# que un rojo honesto). Si cualquiera de los prerequisitos falla, Make se
# detiene ahi mismo (comportamiento estandar de dependencias de Make) y
# 'all' nunca llega a intentar compilar el PDF.
#
# Compilacion con latexmk (no pdflatex+bibtex a mano en 3 pasadas): latexmk
# resuelve solo cuantas pasadas hacen falta (incluyendo bibtex/biber), en
# vez de asumir que 3 siempre alcanza. Probado real contra
# docs/informe-final.tex antes de adoptarlo (mismo PDF de 92 paginas que
# ya generaba el proceso manual).
#
# El informe LaTeX: busca informe-final.tex (nombre definitivo del
# cierre); si no existe, usa informe-entrega-3.tex con un aviso de rename
# pendiente. Si no existe NINGUNO de los dos, falla con mensaje
# explicativo en vez de un error criptico de latexmk.
all: check-latexmk up test bench audit docs
	@echo "Compilando PDF final..."
	@if [ -f docs/informe-final.tex ]; then \
		TEXFILE=docs/informe-final.tex; \
	elif [ -f docs/informe-entrega-3.tex ]; then \
		echo "AVISO: no se encontro docs/informe-final.tex, usando docs/informe-entrega-3.tex" >&2; \
		echo "       -- confirmar con el equipo de documentacion si falta el rename a" >&2; \
		echo "       informe-final.tex antes del cierre." >&2; \
		TEXFILE=docs/informe-entrega-3.tex; \
	else \
		echo "ERROR: no se encontro ni docs/informe-final.tex ni docs/informe-entrega-3.tex --" >&2; \
		echo "       ver equipo de documentacion (el PDF final es requisito de la entrega)." >&2; \
		exit 1; \
	fi; \
	cd docs && latexmk -pdf -interaction=nonstopmode -halt-on-error $$(basename $$TEXFILE)
	@echo "make all: completo -- stack levantado, tests y benchmark corridos, auditorias re-verificadas, evidencia documental regenerada, PDF final compilado."

# make clean: baja los contenedores incluyendo volumenes (borra datos de
# Postgres) y limpia artefactos de build locales (target/, dist/, cache
# de node_modules, db/init/ generado) para forzar una reconstruccion limpia.
clean:
	docker compose down -v
	rm -rf backend-springboot/target
	rm -rf frontend-angular/dist
	rm -rf frontend-angular/node_modules/.cache
	rm -rf db/init

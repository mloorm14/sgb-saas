.PHONY: up down test test-backend test-frontend bench audit clean all check-pdflatex

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
# No exporta CHROME_BIN a mano: se probo en una maquina Windows limpia
# (sin Chrome instalado) que karma-chrome-launcher busca por defecto
# en C:\Program Files\Google\Chrome\Application\chrome.exe y falla si
# no esta -- eso rompia D.1/R1 (make all debe correr en cualquier
# maquina desde una clonacion limpia). frontend-angular/karma.conf.js
# (ver angular.json -> test.options.karmaConfig) resuelve CHROME_BIN
# solo, en este orden: 1) si ya esta seteado, lo respeta: 2) un
# Chrome/Edge/Chromium ya instalado en la maquina (cubre la gran
# mayoria de casos reales); 3) como ultimo recurso, el Chromium que
# instala 'puppeteer' (devDependency). Ese ultimo paso ya NO se baja
# solo con 'npm install'/'npm ci' (ver frontend-angular/.puppeteerrc.cjs):
# se confirmo un 403 real descargandolo en una red con lista blanca de
# dominios, lo que hacia fallar el 'npm ci' completo -- ver
# docs/despliegue/DEPLOYMENT.md, seccion 10, para el detalle. Este
# target no necesita ninguna variable de entorno ni navegador
# preinstalado en la maquina para funcionar en el caso comun.
test-frontend:
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
	@last=$$(ls docs/mediciones/perf/k6-run*.json 2>/dev/null | sed -E 's/.*k6-run([0-9]+)\.json/\1/' | sort -n | tail -1); \
	next=$$(( $${last:-0} + 1 )); \
	echo "Corrida k6 -> docs/mediciones/perf/k6-run$$next.json"; \
	MSYS_NO_PATHCONV=1 docker run --rm --network sgb-saas_default \
		-v "$$(pwd)/k6:/scripts" -v "$$(pwd)/docs/mediciones/perf:/out" \
		grafana/k6 run --out json=/out/k6-run$$next.json /scripts/libros-listado-test.js; \
	echo ""; \
	echo "Resumen (p50/p95) de esta corrida:"; \
	python3 scripts/perf-analysis.py docs/mediciones/perf/k6-run$$next.json | grep -E '"escenario"|"n_peticiones"|"p50_ms"|"p95_ms"'

# make audit: Bloque C.2 -- re-verificacion automatizada de los 4 controles
# OWASP ya documentados manualmente en docs/mediciones/sec/ (A01, A03, A07,
# A09) contra el stack Docker real, incluyendo el paso de verificacion de
# correo que ahora exige el login (ver scripts/owasp-audit.sh). A02 y A05
# quedan fuera (ver comentario en el propio script). Genera un .md nuevo en
# docs/mediciones/sec/ sin tocar los archivos de evidencia originales.
audit:
	bash scripts/owasp-audit.sh

# make clean: baja los contenedores incluyendo volumenes (borra datos de
# Postgres) y limpia artefactos de build locales (target/, dist/, cache
# de node_modules, db/init/ generado) para forzar una reconstruccion limpia.
clean:
	docker compose down -v
	rm -rf backend-springboot/target
	rm -rf frontend-angular/dist
	rm -rf frontend-angular/node_modules/.cache
	rm -rf db/init

# check-pdflatex: verificacion temprana de que pdflatex/bibtex existen en
# este entorno, antes de gastar tiempo en 'up'/'test'/'bench'/'audit' (que
# pueden tardar varios minutos) solo para fallar al final compilando el
# PDF. Falla con un mensaje claro en vez del error generico "command not
# found" que daria Make si se llamara a pdflatex directamente sin este
# chequeo. No instala nada -- instalar una distribucion TeX (MiKTeX, TeX
# Live, etc.) es una decision del entorno de quien corre esto, fuera del
# alcance de este Makefile.
check-pdflatex:
	@command -v pdflatex >/dev/null 2>&1 || { \
		echo "ERROR: pdflatex no esta instalado en este entorno."; \
		echo "'make all' necesita compilar docs/informe-final.pdf (requisito D.1) e instala una distribucion TeX (ej. MiKTeX en Windows, TeX Live en Linux/Mac)."; \
		exit 1; \
	}
	@command -v bibtex >/dev/null 2>&1 || { \
		echo "ERROR: bibtex no esta instalado en este entorno (viene con la misma distribucion TeX que pdflatex)."; \
		exit 1; \
	}

# make all: Bloque D.1 / criterio R1 -- secuencia completa desde una
# clonacion limpia hasta el PDF final compilado. Reusa los targets ya
# existentes como prerequisitos de Make (cada uno corre una sola vez, en
# el orden listado) en vez de duplicar su logica:
#   check-pdflatex -- falla temprano si faltan las herramientas de LaTeX,
#                      antes de gastar tiempo en el resto.
#   up             -- regenera db/init/ y levanta Postgres/Redis/backend/
#                      frontend con las semillas de db/seed.sql aplicadas.
#   test           -- test-backend (Maven: JUnit + JaCoCo + spotbugs) y
#                      test-frontend (Karma), ver targets de arriba.
#   bench          -- corrida de carga real con k6 contra el stack de
#                      'up', con resumen p50/p95 impreso.
#   audit          -- re-verificacion automatizada de los 4 controles
#                      OWASP (scripts/owasp-audit.sh), genera su propio
#                      .md en docs/mediciones/sec/.
# Si cualquiera de esos prerequisitos falla, Make se detiene ahi mismo
# (comportamiento estandar de dependencias de Make) y 'all' nunca llega a
# intentar compilar el PDF -- no hace falta logica adicional para eso.
# Despues de los prerequisitos, compila docs/informe-final.pdf con el
# mismo procedimiento de 3 pasadas de pdflatex + bibtex ya usado en el
# resto de este proyecto (ver docs/informe-final.tex).
all: check-pdflatex up test bench audit
	@echo "Compilando docs/informe-final.pdf..."
	cd docs && pdflatex -interaction=nonstopmode informe-final.tex > /tmp/make-all-pdflatex-1.log 2>&1 || { echo "ERROR: fallo la primera pasada de pdflatex -- ver /tmp/make-all-pdflatex-1.log"; exit 1; }
	cd docs && bibtex informe-final > /tmp/make-all-bibtex.log 2>&1 || { echo "ERROR: fallo bibtex -- ver /tmp/make-all-bibtex.log"; exit 1; }
	cd docs && pdflatex -interaction=nonstopmode informe-final.tex > /tmp/make-all-pdflatex-2.log 2>&1 || { echo "ERROR: fallo la segunda pasada de pdflatex -- ver /tmp/make-all-pdflatex-2.log"; exit 1; }
	cd docs && pdflatex -interaction=nonstopmode informe-final.tex > /tmp/make-all-pdflatex-3.log 2>&1 || { echo "ERROR: fallo la tercera pasada de pdflatex -- ver /tmp/make-all-pdflatex-3.log"; exit 1; }
	@echo "make all: completo -- stack levantado, tests y benchmark corridos, auditoria OWASP re-verificada, docs/informe-final.pdf compilado."

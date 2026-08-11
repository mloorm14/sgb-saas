.PHONY: up down test bench audit clean

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
test-frontend:
	cd frontend-angular && npx ng test --watch=false --browsers=ChromeHeadless

# make bench: placeholder de pruebas de carga/rendimiento. Se implementara
# en el Bloque C.1 con k6; por ahora solo informa el estado pendiente.
bench:
	@echo "Pendiente: requiere k6 (Bloque C.1), ver docs/mediciones/perf/"

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

#!/usr/bin/env python3
"""
scripts/perf-analysis.py — Bloque C.1: agrega las 3 corridas crudas de k6
(docs/mediciones/perf/k6-run{1,2,3}.json, formato --out json= de k6) y
calcula media, mediana, desviación típica, IC 95%, percentiles p50/p90/p95/p99,
tasa de errores HTTP >=500 y throughput, separado por escenario
(cache_caliente / cache_frio).

No usa aleatoriedad propia (es puramente agregación aritmética sobre datos
ya generados), por lo que no aplica semilla fija aquí — la semilla fija del
Bloque C.1 vive en k6/libros-listado-test.js (PRNG del escenario cache_frio).

Uso:
    python scripts/perf-analysis.py docs/mediciones/perf/k6-run1.json \
        docs/mediciones/perf/k6-run2.json docs/mediciones/perf/k6-run3.json
"""
import json
import math
import statistics
import sys
from collections import defaultdict

SCENARIOS = ("cache_caliente", "cache_frio")


def cargar_puntos_http_req_duration(paths):
    """Devuelve {escenario: [(run_idx, duracion_ms, status), ...]}."""
    por_escenario = defaultdict(list)
    for run_idx, path in enumerate(paths, start=1):
        with open(path, "r", encoding="utf-8") as f:
            for linea in f:
                linea = linea.strip()
                if not linea:
                    continue
                obj = json.loads(linea)
                if obj.get("type") != "Point":
                    continue
                if obj.get("metric") != "http_req_duration":
                    continue
                tags = obj["data"].get("tags", {})
                escenario = tags.get("scenario")
                if escenario not in SCENARIOS:
                    continue
                valor = obj["data"]["value"]
                status = tags.get("status", "0")
                por_escenario[escenario].append((run_idx, valor, status))
    return por_escenario


def ic95_media(valores):
    """IC 95% de la media asumiendo normalidad asintótica (n grande, ~t≈z=1.96)."""
    n = len(valores)
    if n < 2:
        return (float("nan"), float("nan"))
    media = statistics.mean(valores)
    desv = statistics.stdev(valores)
    error_estandar = desv / math.sqrt(n)
    margen = 1.96 * error_estandar
    return (media - margen, media + margen)


def percentil(valores_ordenados, p):
    """Percentil por interpolación lineal (método 'nearest-rank' interpolado)."""
    if not valores_ordenados:
        return float("nan")
    n = len(valores_ordenados)
    if n == 1:
        return valores_ordenados[0]
    k = (p / 100) * (n - 1)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return valores_ordenados[int(k)]
    d0 = valores_ordenados[f] * (c - k)
    d1 = valores_ordenados[c] * (k - f)
    return d0 + d1


def analizar_escenario(nombre, puntos, duracion_ventana_s):
    valores = [v for (_run, v, _status) in puntos]
    errores_5xx = sum(1 for (_run, _v, status) in puntos if status and status[0] == "5")
    total = len(puntos)
    valores_ordenados = sorted(valores)

    media = statistics.mean(valores) if valores else float("nan")
    mediana = statistics.median(valores) if valores else float("nan")
    desv = statistics.stdev(valores) if len(valores) > 1 else float("nan")
    ic_lo, ic_hi = ic95_media(valores) if valores else (float("nan"), float("nan"))

    return {
        "escenario": nombre,
        "n_peticiones": total,
        "media_ms": media,
        "mediana_ms": mediana,
        "desviacion_tipica_ms": desv,
        "ic95_media_ms": (ic_lo, ic_hi),
        "p50_ms": percentil(valores_ordenados, 50),
        "p90_ms": percentil(valores_ordenados, 90),
        "p95_ms": percentil(valores_ordenados, 95),
        "p99_ms": percentil(valores_ordenados, 99),
        "tasa_error_5xx": (errores_5xx / total) if total else float("nan"),
        "throughput_req_s": (total / duracion_ventana_s) if duracion_ventana_s else float("nan"),
    }


def main():
    if len(sys.argv) < 2:
        print("Uso: python scripts/perf-analysis.py <run1.json> <run2.json> <run3.json> [...]")
        sys.exit(1)

    paths = sys.argv[1:]
    por_escenario = cargar_puntos_http_req_duration(paths)

    # Ventana de carga sostenida real de cada escenario (ver k6/opts.js):
    # 10s ramp-up + 30s sostenido + 10s ramp-down = 50s por escenario.
    duracion_ventana_s = 50

    resultados = []
    for escenario in SCENARIOS:
        puntos = por_escenario.get(escenario, [])
        resultados.append(analizar_escenario(escenario, puntos, duracion_ventana_s))

    print(json.dumps(resultados, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()

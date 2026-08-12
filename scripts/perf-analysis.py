#!/usr/bin/env python3
"""
scripts/perf-analysis.py — Bloque C.1: agrega las N corridas crudas de k6
(docs/mediciones/perf/k6-run{1..N}.json, formato --out json= de k6) y
calcula media, mediana, desviación típica, IC 95%, percentiles p50/p90/p95/p99,
tasa de errores HTTP >=500 y throughput, separado por escenario
(cache_caliente / cache_frio).

Además (Bloque D4/B.10, cierre de rendimiento): compara cache_caliente vs
cache_frio con un test pareado (Wilcoxon de rangos con signo, sobre el p95
de cada corrida — pareado porque ambos escenarios corren en las mismas N
sesiones/corridas, no son muestras independientes) y su tamaño de efecto
(Cliff's delta).

La agregación en sí es puramente aritmética sobre datos ya generados, sin
aleatoriedad propia. La única excepción es el IC 95% del p95 del gráfico
(`p95-comparacion-escenarios.svg`): un percentil no tiene una fórmula
cerrada de IC como la media, así que se estima por bootstrap (remuestreo
con reemplazo) — ahí sí se fija una semilla (42, mismo valor que el PRNG
de `k6/libros-listado-test.js`, por consistencia) para que el gráfico sea
reproducible byte a byte entre corridas del script.

Uso:
    python scripts/perf-analysis.py docs/mediciones/perf/k6-run1.json \
        docs/mediciones/perf/k6-run2.json docs/mediciones/perf/k6-run3.json
"""
import json
import math
import random
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


def p95_por_corrida(por_escenario):
    """Devuelve {escenario: [p95_run1, p95_run2, ...]}, una serie ordenada
    por índice de corrida (1-based, según el orden de los paths en argv) —
    es la entrada del test pareado: cada posición i de ambas series
    corresponde a la MISMA corrida (misma sesión de carga), no a corridas
    independientes."""
    resultado = {}
    for escenario in SCENARIOS:
        por_run = defaultdict(list)
        for run_idx, valor, _status in por_escenario.get(escenario, []):
            por_run[run_idx].append(valor)
        serie = []
        for run_idx in sorted(por_run.keys()):
            valores_ordenados = sorted(por_run[run_idx])
            serie.append(percentil(valores_ordenados, 95))
        resultado[escenario] = serie
    return resultado


def _wilcoxon_manual(x, y):
    """Wilcoxon de rangos con signo, pareado, implementado a mano (fallback
    si scipy no está disponible). Aproximación normal con corrección de
    continuidad -- válida para n moderado; para n muy pequeño (ej. 5 pares)
    es menos precisa que el método exacto de scipy, pero da un p-valor
    utilizable sin dependencias externas."""
    diffs = [xi - yi for xi, yi in zip(x, y) if xi != yi]
    n = len(diffs)
    if n == 0:
        return 0.0, 1.0

    abs_diffs = sorted(range(n), key=lambda i: abs(diffs[i]))
    ranks = [0.0] * n
    i = 0
    rank_actual = 1
    while i < n:
        j = i
        while j + 1 < n and abs(diffs[abs_diffs[j + 1]]) == abs(diffs[abs_diffs[i]]):
            j += 1
        rango_promedio = (rank_actual + (rank_actual + (j - i))) / 2
        for k in range(i, j + 1):
            ranks[abs_diffs[k]] = rango_promedio
        rank_actual += (j - i + 1)
        i = j + 1

    w_pos = sum(ranks[i] for i in range(n) if diffs[i] > 0)
    w_neg = sum(ranks[i] for i in range(n) if diffs[i] < 0)
    estadistico = min(w_pos, w_neg)

    media = n * (n + 1) / 4
    desv = math.sqrt(n * (n + 1) * (2 * n + 1) / 24)
    if desv == 0:
        return estadistico, 1.0
    z = (estadistico - media + (0.5 if estadistico < media else -0.5)) / desv
    # Función de distribución normal estándar acumulada vía erf, sin depender de scipy.
    p_valor = 2 * (1 - 0.5 * (1 + math.erf(abs(z) / math.sqrt(2))))
    p_valor = min(1.0, p_valor)
    return estadistico, p_valor


def wilcoxon_pareado(x, y):
    """Test de Wilcoxon de rangos con signo (pareado, no Mann-Whitney: x[i]
    e y[i] son la misma corrida). Usa scipy.stats.wilcoxon si está
    disponible (método exacto para n pequeño); si no, cae al cálculo manual
    sobre rangos con aproximación normal."""
    try:
        from scipy import stats as scipy_stats
        resultado = scipy_stats.wilcoxon(x, y)
        return float(resultado.statistic), float(resultado.pvalue), "scipy.stats.wilcoxon"
    except ImportError:
        estadistico, p_valor = _wilcoxon_manual(x, y)
        return estadistico, p_valor, "manual (aproximación normal, scipy no disponible)"


def cliffs_delta(x, y):
    """Tamaño de efecto no paramétrico de Cliff: para cada par (xi de x, yj
    de y), cuenta cuántas veces xi < yj menos cuántas veces xi > yj,
    dividido por n*m. Rango [-1, 1]; magnitud cercana a 1 = separación casi
    total entre las dos distribuciones, cercana a 0 = solapadas."""
    n, m = len(x), len(y)
    if n == 0 or m == 0:
        return float("nan")
    mayor = 0
    menor = 0
    for xi in x:
        for yj in y:
            if xi > yj:
                mayor += 1
            elif xi < yj:
                menor += 1
    return (menor - mayor) / (n * m)


def interpretar_cliffs_delta(delta):
    """Convenciones estándar (Romano et al. 2006) sobre |delta|."""
    d = abs(delta)
    if d < 0.147:
        return "despreciable"
    if d < 0.33:
        return "pequeño"
    if d < 0.474:
        return "mediano"
    return "grande"


BOOTSTRAP_SEED = 42
BOOTSTRAP_N = 2000


def bootstrap_ic95_percentil(valores, p=95, n_boot=BOOTSTRAP_N, seed=BOOTSTRAP_SEED):
    """IC 95% del percentil `p` por bootstrap (remuestreo con reemplazo,
    `n_boot` réplicas, semilla fija para reproducibilidad). Un percentil no
    tiene una fórmula cerrada de IC como la media (no aplica ic95_media
    aquí), por eso se estima empíricamente. Usa numpy si está disponible
    (vectorizado, mucho más rápido); si no, cae a un bucle puro en Python
    con el mismo algoritmo."""
    n = len(valores)
    if n < 2:
        return (float("nan"), float("nan"))
    try:
        import numpy as np
        rng = np.random.default_rng(seed)
        arr = np.asarray(valores, dtype=float)
        idx = rng.integers(0, n, size=(n_boot, n))
        replicas = np.percentile(arr[idx], p, axis=1)
        lo, hi = np.percentile(replicas, [2.5, 97.5])
        return float(lo), float(hi)
    except ImportError:
        rng = random.Random(seed)
        replicas = []
        for _ in range(n_boot):
            muestra = sorted(valores[rng.randrange(n)] for _ in range(n))
            replicas.append(percentil(muestra, p))
        replicas.sort()
        return percentil(replicas, 2.5), percentil(replicas, 97.5)


def generar_grafico_p95(por_escenario, output_path):
    """Gráfico de barras vectorial (SVG): p95 por corrida, cache_caliente
    vs cache_frio, con barras de error de IC 95% (bootstrap). Paleta
    Okabe-Ito (accesible a daltonismo): naranja #E69F00 / celeste #56B4E9."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    COLOR_CALIENTE = "#E69F00"
    COLOR_FRIO = "#56B4E9"

    datos = {}
    for escenario in SCENARIOS:
        por_run = defaultdict(list)
        for run_idx, valor, _status in por_escenario.get(escenario, []):
            por_run[run_idx].append(valor)
        runs_ordenados = sorted(por_run.keys())
        p95_valores = []
        err_lo = []
        err_hi = []
        for run_idx in runs_ordenados:
            valores = por_run[run_idx]
            p95 = percentil(sorted(valores), 95)
            lo, hi = bootstrap_ic95_percentil(valores)
            p95_valores.append(p95)
            err_lo.append(max(0.0, p95 - lo))
            err_hi.append(max(0.0, hi - p95))
        datos[escenario] = {
            "runs": runs_ordenados,
            "p95": p95_valores,
            "err": [err_lo, err_hi],
        }

    runs = datos["cache_caliente"]["runs"]
    x = list(range(len(runs)))
    width = 0.35

    fig, ax = plt.subplots(figsize=(8, 5))
    ax.bar(
        [i - width / 2 for i in x], datos["cache_caliente"]["p95"], width,
        yerr=datos["cache_caliente"]["err"], capsize=4,
        label="cache_caliente", color=COLOR_CALIENTE,
    )
    ax.bar(
        [i + width / 2 for i in x], datos["cache_frio"]["p95"], width,
        yerr=datos["cache_frio"]["err"], capsize=4,
        label="cache_frio", color=COLOR_FRIO,
    )
    ax.set_xticks(x)
    ax.set_xticklabels([f"Corrida {i}" for i in runs])
    ax.set_ylabel("p95 http_req_duration (ms)")
    ax.set_xlabel("Corrida")
    ax.set_title("p95 por corrida — cache_caliente vs cache_frio (barras de error: IC 95% bootstrap)")
    ax.legend()
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    fig.tight_layout()
    fig.savefig(output_path, format="svg")
    plt.close(fig)


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

    # Comparación pareada cache_caliente vs cache_frio sobre el p95 de cada
    # corrida individual (no sobre el pool de peticiones): son N corridas de
    # la misma sesión de carga, por eso el test es pareado (Wilcoxon), no
    # Mann-Whitney (que asumiría dos muestras independientes).
    serie_p95 = p95_por_corrida(por_escenario)
    x = serie_p95["cache_caliente"]
    y = serie_p95["cache_frio"]

    print("\n--- Comparación pareada cache_caliente vs cache_frio (p95 por corrida) ---")
    print(f"p95 cache_caliente por corrida: {[round(v, 2) for v in x]}")
    print(f"p95 cache_frio por corrida:     {[round(v, 2) for v in y]}")

    if len(x) < 2 or len(x) != len(y):
        print("Se necesitan al menos 2 corridas pareadas (mismo N en ambos escenarios) "
              "para el test de Wilcoxon y Cliff's delta -- omitido.")
        return

    estadistico, p_valor, metodo = wilcoxon_pareado(x, y)
    delta = cliffs_delta(x, y)
    interpretacion = interpretar_cliffs_delta(delta)

    print(f"Wilcoxon (rangos con signo, pareado, método: {metodo}):")
    print(f"  estadístico = {estadistico:.4f}")
    print(f"  p-valor     = {p_valor:.6f}")
    print(f"  {'Diferencia estadísticamente significativa (p<0.05)' if p_valor < 0.05 else 'Sin diferencia estadísticamente significativa (p>=0.05)'}")
    print(f"Cliff's delta = {delta:.4f} (efecto {interpretacion}; "
          f"{'cache_caliente tiende a ser MÁS lento' if delta < 0 else 'cache_frio tiende a ser MÁS lento' if delta > 0 else 'sin tendencia'})")

    grafico_path = "docs/mediciones/perf/p95-comparacion-escenarios.svg"
    try:
        generar_grafico_p95(por_escenario, grafico_path)
        print(f"\nGráfico guardado en {grafico_path}")
    except ImportError as ex:
        print(f"\nAVISO: no se generó el gráfico ({grafico_path}) -- falta matplotlib: {ex}")


if __name__ == "__main__":
    main()

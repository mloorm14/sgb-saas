#!/usr/bin/env python3
"""Audit Spanish roots in backend Java identifiers.

The final-rubric check is intentionally conservative: it scans class-like
declarations and method declarations in src/main/java and reports identifiers
whose camel-case tokens match common Spanish domain words used before the
English renaming pass.
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


SPANISH_ROOTS = {
    "anular",
    "apellido",
    "autor",
    "bibliotecario",
    "bitacora",
    "buscar",
    "categoria",
    "configuracion",
    "correo",
    "crear",
    "devolucion",
    "devolver",
    "editorial",
    "eliminar",
    "estado",
    "idioma",
    "lector",
    "libro",
    "listar",
    "multa",
    "nombre",
    "notificacion",
    "obtener",
    "pago",
    "pendiente",
    "prestamo",
    "proveedor",
    "renovar",
    "reservacion",
    "respaldo",
    "sugerencia",
    "usuario",
}

TYPE_RE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)")
METHOD_RE = re.compile(
    r"^\s*(?:public|protected|private)\s+"
    r"(?:static\s+)?(?:final\s+)?[\w<>\[\], ?.@]+\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*\("
)


@dataclass(frozen=True)
class Finding:
    kind: str
    identifier: str
    token: str
    path: Path
    line: int


def split_identifier(identifier: str) -> list[str]:
    parts = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", identifier)
    parts = re.sub(r"[^A-Za-z0-9]+", " ", parts)
    return [part.lower() for part in parts.split() if part]


def scan_file(path: Path) -> list[Finding]:
    findings: list[Finding] = []
    for number, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), start=1):
        for kind, regex in (("type", TYPE_RE), ("method", METHOD_RE)):
            match = regex.search(line)
            if not match:
                continue
            identifier = match.group(1)
            tokens = split_identifier(identifier)
            for token in tokens:
                if token in SPANISH_ROOTS:
                    findings.append(Finding(kind, identifier, token, path, number))
                    break
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit backend Java names for Spanish roots.")
    parser.add_argument("--root", default="backend-springboot/src/main/java", help="Java source root")
    parser.add_argument("--max-percent", type=float, default=5.0, help="Rubric threshold for Complete")
    args = parser.parse_args()

    root = Path(args.root)
    files = sorted(root.rglob("*.java"))
    findings: list[Finding] = []
    type_total = 0
    method_total = 0

    for path in files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        type_total += len(TYPE_RE.findall(text))
        method_total += sum(1 for line in text.splitlines() if METHOD_RE.search(line))
        findings.extend(scan_file(path))

    type_findings = [f for f in findings if f.kind == "type"]
    method_findings = [f for f in findings if f.kind == "method"]
    type_pct = (len(type_findings) / type_total * 100) if type_total else 0.0
    method_pct = (len(method_findings) / method_total * 100) if method_total else 0.0
    worst = max(type_pct, method_pct)

    print(f"Types: {len(type_findings)}/{type_total} flagged ({type_pct:.2f}%)")
    print(f"Methods: {len(method_findings)}/{method_total} flagged ({method_pct:.2f}%)")
    print(f"Worst rubric percentage: {worst:.2f}%")
    if findings:
        print()
        for finding in findings:
            print(f"{finding.path}:{finding.line}: {finding.kind} {finding.identifier} -> {finding.token}")

    return 0 if worst <= args.max_percent else 1


if __name__ == "__main__":
    raise SystemExit(main())

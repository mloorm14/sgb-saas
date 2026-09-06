import os

path = 'frontend-angular/src/app/shared/reservaciones-hoy-card/reservaciones-hoy-card.component.ts'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import { Component, input, output } from \'@angular/core\';', 'import { Component, input, output, computed, signal } from \'@angular/core\';\nimport { Router } from \'@angular/router\';')
content = content.replace('readonly marcarListaParaRetiro = output<number>();', '''readonly marcarListaParaRetiro = output<number>();

  pagina = signal(0);
  readonly tamanoPagina = 10;

  paginadas = computed(() => {
    const inicio = this.pagina() * this.tamanoPagina;
    return this.reservaciones().slice(inicio, inicio + this.tamanoPagina);
  });

  totalPaginas = computed(() => Math.max(1, Math.ceil(this.reservaciones().length / this.tamanoPagina)));
  puedeAnterior = computed(() => this.pagina() > 0);
  puedeSiguiente = computed(() => this.pagina() < this.totalPaginas() - 1);
  paginas = computed(() => Array.from({ length: this.totalPaginas() }, (_, i) => i));

  constructor(private router: Router) {}

  irAPagina(p: number) {
    if (p >= 0 && p < this.totalPaginas() && p !== this.pagina()) this.pagina.set(p);
  }
  paginaAnterior() { if (this.puedeAnterior()) this.pagina.update(p => p - 1); }
  paginaSiguiente() { if (this.puedeSiguiente()) this.pagina.update(p => p + 1); }

  irAReservarAhora(correo: string) {
    this.router.navigate(['/dashboard-bibliotecario/reservaciones'], { queryParams: { q: correo } });
  }''')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)
print("Updated TS")

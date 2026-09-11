import os

path = 'frontend-angular/src/app/shared/reservaciones-hoy-card/reservaciones-hoy-card.component.html'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('@for (r of reservaciones();', '@for (r of paginadas();')

content = content.replace('''@if (r.estadoNombre === 'PENDIENTE') {
                <button (click)="marcarListaParaRetiro.emit(r.reservacionId)"
                  class="h-8 px-sm rounded-lg bg-primary text-on-primary font-label-sm text-[12px] hover:opacity-90 transition-opacity">
                  Marcar lista
                </button>
              }''', '''@if (r.estadoNombre === 'PENDIENTE') {
                <button (click)="irAReservarAhora(r.usuarioCorreo)"
                  class="h-8 px-sm rounded-lg bg-primary text-on-primary font-label-sm text-[12px] hover:opacity-90 transition-opacity">
                  Reservar ahora
                </button>
              }''')

content = content.replace('''@if (r.estadoNombre === 'PENDIENTE') {
              <button (click)="marcarListaParaRetiro.emit(r.reservacionId)"
                class="shrink-0 h-8 px-sm rounded-lg bg-primary text-on-primary font-label-sm text-[12px] hover:opacity-90 transition-opacity">
                Marcar lista
              </button>
            }''', '''@if (r.estadoNombre === 'PENDIENTE') {
              <button (click)="irAReservarAhora(r.usuarioCorreo)"
                class="shrink-0 h-8 px-sm rounded-lg bg-primary text-on-primary font-label-sm text-[12px] hover:opacity-90 transition-opacity">
                Reservar ahora
              </button>
            }''')

paginador = """    <div class="flex flex-wrap justify-between items-center mt-lg gap-sm">
      <div class="flex items-center gap-xs font-body-sm text-body-sm">
        <span class="text-on-surface-variant">Mostrar 10 por p?gina</span>
      </div>
      @if (paginas().length > 1) {
        <div class="flex items-center gap-xs">
          <button (click)="paginaAnterior()" [disabled]="!puedeAnterior()" class="h-8 min-h-[44px] min-w-[44px] px-3 rounded border border-outline-variant disabled:opacity-40 cursor-pointer font-body-sm text-body-sm flex items-center gap-xs"><span class="material-symbols-outlined text-[18px]">chevron_left</span> Anterior</button>
          @for (p of paginas(); track p) {
            <button (click)="irAPagina(p)" class="w-8 h-8 min-h-[44px] min-w-[44px] rounded font-label-sm text-label-sm cursor-pointer" [class.bg-primary]="p===pagina()" [class.text-on-primary]="p===pagina()" [class.border]="p!==pagina()" [class.border-outline-variant]="p!==pagina()" [class.hover:bg-surface-container-low]="p!==pagina()">{{ p + 1 }}</button>
          }
          <button (click)="paginaSiguiente()" [disabled]="!puedeSiguiente()" class="h-8 min-h-[44px] min-w-[44px] px-3 rounded border border-outline-variant disabled:opacity-40 cursor-pointer font-body-sm text-body-sm flex items-center gap-xs">Siguiente <span class="material-symbols-outlined text-[18px]">chevron_right</span></button>
        </div>
      }
    </div>
  }
</div>
"""

content = content.replace('  }\n</div>', paginador)

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)
print("Updated card html")

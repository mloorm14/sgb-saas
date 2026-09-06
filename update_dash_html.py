import os

path = 'frontend-angular/src/app/dashboard/dashboard-bibliotecario-home.component.html'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

select_block = """      <div class="flex items-center gap-xs font-body-sm text-body-sm">
        <span class="text-on-surface-variant">Mostrar</span>
        <select [ngModel]="tamanoPaginaProximas" (ngModelChange)="cambiarTamanoProximas($event)" class="h-10 px-sm pr-8 rounded border border-outline-variant bg-surface-container-lowest cursor-pointer appearance-none text-xs leading-tight py-1.5">
          <option [ngValue]="10">10</option><option [ngValue]="20">20</option><option [ngValue]="30">30</option>
        </select><span class="text-on-surface-variant">por p?gina</span>
      </div>"""

fixed_block = """      <div class="flex items-center gap-xs font-body-sm text-body-sm">
        <span class="text-on-surface-variant">Mostrar 10 por p?gina</span>
      </div>"""

content = content.replace(select_block, fixed_block)

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)
print("Updated dashboard html")

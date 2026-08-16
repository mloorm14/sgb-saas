import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';
import { SugerenciaAdquisicionRequest } from '../../core/models/sugerencia-adquisicion.model';

// Formulario de sugerencia de adquisición (Rama B, mockup 07). Las
// validaciones replican EXACTAS a SugerenciaAdquisicionRequestDTO del
// backend (verificado en backend-springboot): titulo requerido max 255,
// autor opcional max 150, isbn opcional con patrón ^[0-9\-]{10,17}$ max 13,
// justificacion opcional max 1000.
@Component({
  selector: 'app-sugerencias-form',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './sugerencias-form.component.html'
})
export class SugerenciasFormComponent implements OnInit {
  form: FormGroup;
  cargando: boolean = false;
  errorMsg: string = '';

  constructor(
    private fb: FormBuilder,
    private sugerenciaService: SugerenciaAdquisicionService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.form = this.fb.group({
      titulo: ['', [Validators.required, Validators.maxLength(255)]],
      autor: ['', [Validators.maxLength(150)]],
      isbn: ['', [Validators.maxLength(13), Validators.pattern(/^[0-9\-]{10,17}$/)]],
      justificacion: ['', [Validators.maxLength(1000)]]
    });
  }

  ngOnInit(): void {
    // Prellenado desde el detalle de libro (query param titulo).
    const titulo = this.route.snapshot.queryParamMap.get('titulo');
    if (titulo) {
      this.form.patchValue({ titulo });
    }
  }

  campoInvalido(campo: string): boolean {
    const control = this.form.get(campo);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  enviar(): void {
    if (this.form.invalid) return;
    this.cargando = true;
    this.errorMsg = '';

    // Los opcionales con @Pattern (isbn) fallan en el backend si llega ''
    // en vez de omitirse -- Validators.pattern salta vacíos pero @Pattern
    // no (solo salta null), y el regex exige min 10 chars. @Size solo
    // (autor, justificacion) es inofensivo con '', pero se limpia igual
    // por consistencia.
    const dto: Partial<SugerenciaAdquisicionRequest> = { ...this.form.value };
    (['autor', 'isbn', 'justificacion'] as const).forEach((campo) => {
      if (!dto[campo]) delete dto[campo];
    });

    this.sugerenciaService.crear(dto as SugerenciaAdquisicionRequest).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/sugerencias']);
      },
      error: () => {
        this.cargando = false;
        this.errorMsg = 'Error al enviar la sugerencia';
      }
    });
  }
}
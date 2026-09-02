import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';

// Formulario de sugerencia de adquisición (Rama B, mockup 07). Las
// validaciones replican EXACTAS a SugerenciaAdquisicionRequestDTO del
// backend (verificado en backend-springboot): titulo requerido max 255,
// autor opcional max 150, isbn opcional con patrón ^[0-9\-]{10,17}$ max 13,
// justificacion opcional max 1000.
@Component({
  standalone: true,
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

    this.sugerenciaService.crear(this.form.value).subscribe({
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
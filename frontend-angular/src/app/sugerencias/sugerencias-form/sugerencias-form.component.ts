import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';

// Formulario de sugerencia de adquisición: validaciones espejo de
// SugerenciaAdquisicionRequestDTO del backend.
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
      isbn: ['', [Validators.minLength(13), Validators.maxLength(13), Validators.pattern(/^[0-9]{13}$/)]],
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
        this.router.navigate(['/dashboard-lector/sugerencias']);
      },
      error: () => {
        this.cargando = false;
        this.errorMsg = 'Error al enviar la sugerencia';
      }
    });
  }
}
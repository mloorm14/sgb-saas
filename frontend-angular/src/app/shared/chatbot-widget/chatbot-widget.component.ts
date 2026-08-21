import { Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatbotService } from '../../core/services/chatbot.service';
import { ChatMensaje } from '../../core/models/chatbot.model';

@Component({
  selector: 'app-chatbot-widget',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './chatbot-widget.component.html'
})
export class ChatbotWidgetComponent implements OnDestroy {
  expandido = false;
  sesionId: string | null = null;
  mensajes: ChatMensaje[] = [];
  textoMensaje = '';
  cargando = false;
  errorRateLimit = false;
  errorGeneral = '';

  constructor(private chatbotService: ChatbotService) {}

  toggle(): void {
    this.expandido = !this.expandido;
  }

  enviar(): void {
    const texto = this.textoMensaje.trim();
    if (!texto || this.cargando) return;

    this.errorRateLimit = false;
    this.errorGeneral = '';
    this.cargando = true;

    const mensajeUsuario: ChatMensaje = {
      rol: 'usuario',
      texto,
      timestamp: new Date().toISOString()
    };
    this.mensajes.push(mensajeUsuario);
    this.textoMensaje = '';

    this.chatbotService.enviarMensaje(this.sesionId, texto).subscribe({
      next: (respuesta) => {
        this.sesionId = respuesta.sesionId;
        this.mensajes.push({
          rol: 'asistente',
          texto: respuesta.respuesta,
          timestamp: respuesta.timestamp
        });
        this.cargando = false;
      },
      error: (err) => {
        this.cargando = false;
        const status = err?.status;
        if (status === 429) {
          this.errorRateLimit = true;
        } else if (status === 404) {
          this.sesionId = null;
          this.errorGeneral = 'La sesión expiró. Se inició una conversación nueva.';
        } else {
          this.errorGeneral = err?.error?.detail ?? 'Error al enviar el mensaje. Intenta nuevamente.';
        }
      }
    });
  }

  ngOnDestroy(): void {
    // limpieza si se necesita en el futuro
  }
}

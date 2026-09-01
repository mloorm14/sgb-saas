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
        } else if (status === 0 || status === 500 || !navigator.onLine) {
          // Fallback offline/demo: sin backend o sin GEMINI_API_KEY no se queda colgado
          const mock = this.respuestaMockOffline(texto);
          this.mensajes.push({
            rol: 'asistente',
            texto: mock,
            timestamp: new Date().toISOString()
          });
          this.errorGeneral = 'Modo demo offline — el backend/Gemini no está disponible.';
        } else {
          this.errorGeneral = err?.error?.detail ?? 'Error al enviar el mensaje. Intenta nuevamente.';
        }
      }
    });
  }

  private respuestaMockOffline(texto: string): string {
    const t = texto.toLowerCase();
    if (t.includes('hola') || t.includes('buenos')) return '¡Hola! Soy el asistente de Leibri (modo demo offline). Pregúntame por horarios, préstamos, multas o disponibilidad de libros.';
    if (t.includes('disponible') || t.includes('libro') || t.includes('buscar')) return 'En modo demo offline no puedo consultar el catálogo real. Ve a Catálogo y busca por título, o consulta en ventanilla.';
    if (t.includes('préstamo') || t.includes('prestamo')) return 'Para ver tus préstamos ve a “Mis Préstamos”. En modo demo offline no puedo consultar la BD.';
    if (t.includes('multa')) return 'Para multas ve a “Multas”. En demo offline no puedo consultar tu deuda real.';
    if (t.includes('horario')) return 'Horario demo: Lun–Vie 08:00–20:00, Sáb 09:00–13:00.';
    return 'Estoy en modo demo offline (backend/Gemini no disponible). Puedo orientarte sobre préstamos, reservas, multas y catálogo, pero sin datos reales. ¿Qué necesitas?';
  }

  ngOnDestroy(): void {
    // limpieza si se necesita en el futuro
  }
}

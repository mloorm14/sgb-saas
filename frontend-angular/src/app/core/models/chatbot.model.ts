export interface ChatMensaje {
  rol: 'usuario' | 'asistente';
  texto: string;
  timestamp: string;
}

export interface ChatbotRespuesta {
  sesionId: string;
  respuesta: string;
  timestamp: string;
}

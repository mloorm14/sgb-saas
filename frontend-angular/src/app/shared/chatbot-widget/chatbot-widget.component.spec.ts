import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ChatbotWidgetComponent } from './chatbot-widget.component';
import { ChatbotService } from '../../core/services/chatbot.service';

describe('ChatbotWidgetComponent', () => {
  let component: ChatbotWidgetComponent;
  let fixture: ComponentFixture<ChatbotWidgetComponent>;
  let chatbotService: jasmine.SpyObj<ChatbotService>;

  beforeEach(async () => {
    chatbotService = jasmine.createSpyObj('ChatbotService', ['enviarMensaje']);
    chatbotService.enviarMensaje.and.returnValue(of({
      sesionId: 'abc-123',
      respuesta: '¡Hola! ¿En qué puedo ayudarte?',
      timestamp: '2026-08-20T10:00:00Z'
    }));

    await TestBed.configureTestingModule({
      imports: [ChatbotWidgetComponent],
      providers: [
        { provide: ChatbotService, useValue: chatbotService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChatbotWidgetComponent);
    component = fixture.componentInstance;
  });

  it('envía un mensaje y agrega la respuesta del asistente a la lista', () => {
    component.textoMensaje = '¿Tienen libros disponibles?';
    component.enviar();

    expect(chatbotService.enviarMensaje).toHaveBeenCalledWith(null, '¿Tienen libros disponibles?');
    expect(component.mensajes.length).toBe(2);
    expect(component.mensajes[0].rol).toBe('usuario');
    expect(component.mensajes[1].rol).toBe('asistente');
    expect(component.sesionId).toBe('abc-123');
    expect(component.cargando).toBeFalse();
  });

  it('muestra aviso de rate limit cuando el backend responde 429', () => {
    chatbotService.enviarMensaje.and.returnValue(throwError(() => ({ status: 429 })));
    component.textoMensaje = 'Mensaje rápido';
    component.enviar();

    expect(component.errorRateLimit).toBeTrue();
    expect(component.mensajes.length).toBe(1);
    expect(component.cargando).toBeFalse();
  });

  it('reinicia sesionId a null cuando el backend responde 404', () => {
    chatbotService.enviarMensaje.and.returnValue(throwError(() => ({ status: 404 })));
    component.sesionId = 'sesion-vieja';
    component.textoMensaje = 'Mensaje';
    component.enviar();

    expect(component.sesionId).toBeNull();
    expect(component.errorGeneral).toContain('nueva');
    expect(component.cargando).toBeFalse();
  });
});

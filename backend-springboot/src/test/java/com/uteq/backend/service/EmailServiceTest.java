package com.uteq.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// No se usa GreenMail (aunque el roadmap original lo sugiere): con
// JavaMailSender mockeado alcanza para probar las dos ramas que le
// importan a EmailService (éxito / MailException capturada), sin agregar
// una dependencia de test nueva solo para eso -- mismo criterio que el
// resto de la suite (todo mockeado, ver LoginRateLimiterTest).
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String REMITENTE = "no-reply@sgb-saas.local";
    private static final String DESTINATARIO = "lector@uteq.edu.ec";
    private static final String ASUNTO = "Tu préstamo vence pronto";
    private static final String CUERPO = "<p>Recuerda devolver tu libro.</p>";

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "remitente", REMITENTE);
    }

    @Test
    void enviarCorreo_smtpDisponible_retornaTrueYDespachaElMensaje() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        boolean resultado = emailService.enviarCorreo(DESTINATARIO, ASUNTO, CUERPO);

        assertTrue(resultado);
        verify(mailSender).send(mimeMessage);
    }

    // El fallo NUNCA debe propagarse como excepción -- ver Javadoc de
    // EmailService: un préstamo/devolución/registro es válido con o sin
    // correo enviado.
    @Test
    void enviarCorreo_smtpCaido_capturaLaExcepcionYRetornaFalse() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(mimeMessage);

        boolean resultado = emailService.enviarCorreo(DESTINATARIO, ASUNTO, CUERPO);

        assertFalse(resultado);
    }
}

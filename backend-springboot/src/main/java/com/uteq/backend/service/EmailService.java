package com.uteq.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Envuelve {@link JavaMailSender} para los Módulos 2 (alertas de
 * vencimiento/multa/reserva caducada) y 9.5 (código de verificación de
 * correo). Un fallo de SMTP (host caído, credenciales inválidas, timeout)
 * <b>nunca</b> debe romper el flujo que lo origina -- registrar una
 * devolución, generar una multa, o el registro de un usuario nuevo son
 * operaciones válidas por sí mismas, con o sin correo. Por eso este método
 * atrapa {@link MailException}/{@link MessagingException} y devuelve
 * {@code boolean} en vez de relanzar: el llamador decide qué hacer con el
 * resultado (p.ej. {@code NotificacionService} lo persiste en
 * {@code notificaciones.enviado_ok}/{@code error_envio}).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${notificaciones.remitente}")
    private String remitente;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.api-url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * @param cuerpoHtml se envía como HTML (segundo parámetro {@code true}
     *                   de {@link MimeMessageHelper#setText}) para permitir
     *                   énfasis simple (ej. negrita en el título del libro
     *                   o el código de verificación) sin depender de una
     *                   plantilla externa.
     * @return {@code true} si el envío se despachó sin error; {@code false}
     *         si falló -- en cuyo caso ya quedó registrado en el log de la
     *         aplicación, el llamador no necesita loguearlo de nuevo.
     */
    public boolean enviarCorreo(String destinatario, String asunto, String cuerpoHtml) {
        // 1) Intento SMTP clásico (respeta SMTP_* de Render)
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            mailSender.send(mensaje);
            return true;
        } catch (MessagingException | MailException ex) {
            log.warn("SMTP falló para {} (asunto: \"{}\"): {} — probando fallback Brevo API", destinatario, asunto, ex.getMessage());
            // 2) Fallback HTTP Brevo API (no usa puerto SMTP, no depende de Render SMTP_PORT)
            if (brevoApiKey != null && !brevoApiKey.isBlank()) {
                boolean ok = enviarViaBrevoApi(destinatario, asunto, cuerpoHtml);
                if (ok) return true;
            }
            log.error("Fallo al enviar correo a {} (asunto: \"{}\") por SMTP y Brevo API", destinatario, asunto);
            return false;
        }
    }

    private boolean enviarViaBrevoApi(String destinatario, String asunto, String cuerpoHtml) {
        try {
            RestClient client = RestClient.builder().baseUrl("https://api.brevo.com").build();
            Map<String, Object> body = Map.of(
                    "sender", Map.of("email", remitente, "name", "SGB-SaaS"),
                    "to", List.of(Map.of("email", destinatario)),
                    "subject", asunto,
                    "htmlContent", cuerpoHtml
            );
            var resp = client.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", brevoApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            if (ok) log.info("Correo enviado vía Brevo API a {} (asunto: \"{}\")", destinatario, asunto);
            else log.warn("Brevo API respondió {} para {}: {}", resp.getStatusCode(), destinatario, resp.getBody());
            return ok;
        } catch (Exception ex) {
            log.warn("Fallback Brevo API falló para {}: {}", destinatario, ex.getMessage());
            return false;
        }
    }
}

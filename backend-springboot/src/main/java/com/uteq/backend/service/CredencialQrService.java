package com.uteq.backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Genera y resuelve la credencial QR de un usuario (Módulo 8). Decisión de
 * diseño: el QR NO reemplaza el login por correo, es un método adicional de
 * identificación rápida en ventanilla -- el bibliotecario escanea la
 * credencial del estudiante para agilizar el registro de un préstamo (ver
 * PrestamoService.crear()), y el ingreso manual de usuarioId se mantiene
 * siempre disponible como contingencia.
 */
@Service
public class CredencialQrService {

    private static final int TAMANO_PX = 300;
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String CREDENCIAL_NO_RECONOCIDA =
            "Credencial QR no reconocida o usuario inactivo.";

    private final UsuarioRepository usuarioRepo;

    public CredencialQrService(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    /**
     * Genera la imagen PNG del QR del usuario autenticado (GET
     * /mi-credencial). No recibe un usuarioId por parámetro a propósito:
     * cada usuario solo puede pedir SU propio QR, nunca el de otro.
     */
    public byte[] generarImagenQrPropio(Authentication authentication) {
        Usuario usuario = usuarioRepo.findByCorreo(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
        return generarImagenQr(usuario);
    }

    /**
     * Codifica ÚNICAMENTE el {@code credencialQrToken} (UUID) dentro del QR
     * -- nunca el correo ni la identificación en claro, para que el QR
     * físico/impreso no filtre datos personales por sí solo si se pierde o
     * lo ve alguien más.
     */
    private byte[] generarImagenQr(Usuario usuario) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(
                    usuario.getCredencialQrToken().toString(),
                    BarcodeFormat.QR_CODE, TAMANO_PX, TAMANO_PX);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException ex) {
            // No debería ocurrir con un UUID.toString() como contenido (36
            // caracteres ASCII, muy por debajo de la capacidad de un QR) --
            // si pasa, es un problema del entorno (encoder/IO), no del dato.
            throw new IllegalStateException("No se pudo generar el código QR.", ex);
        }
    }

    /**
     * Resuelve el usuario a partir del token leído del QR escaneado en
     * ventanilla. Rechaza tanto un token inexistente como un usuario que no
     * esté en estado ACTIVO (bloqueado por multa, inactivo, pendiente de
     * verificación) con el MISMO mensaje genérico: el bibliotecario ve
     * "credencial no reconocida" en ambos casos, sin filtrar por qué (evita
     * que escanear credenciales ajenas sirva para enumerar qué usuarios
     * existen o están bloqueados), y usa el ingreso manual de cédula como
     * contingencia.
     */
    public Usuario resolverPorToken(UUID token) {
        Usuario usuario = usuarioRepo.findByCredencialQrToken(token)
                .orElseThrow(() -> new EntityNotFoundException(CREDENCIAL_NO_RECONOCIDA));
        if (!ESTADO_ACTIVO.equals(usuario.getEstado().getNombre())) {
            throw new EntityNotFoundException(CREDENCIAL_NO_RECONOCIDA);
        }
        return usuario;
    }
}

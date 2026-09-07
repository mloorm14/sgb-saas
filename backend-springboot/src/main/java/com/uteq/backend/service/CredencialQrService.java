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
 * Genera y resuelve la credencial QR del usuario: identificación rápida en
 * ventanilla para agilizar préstamos (el ingreso manual sigue disponible).
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
     * Genera el QR del usuario autenticado: cada usuario solo puede pedir el suyo.
     */
    public byte[] generarImagenQrPropio(Authentication authentication) {
        Usuario usuario = usuarioRepo.findByCorreo(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
        return generarImagenQr(usuario);
    }

    /**
     * Codifica ÚNICAMENTE el token (UUID): el QR no expone datos personales si se pierde.
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
            // Si pasa, es un problema del entorno (encoder/IO), no del dato.
            throw new IllegalStateException("No se pudo generar el código QR.", ex);
        }
    }

    /**
     * Resuelve el usuario desde el token escaneado en ventanilla.
     * Token inexistente o usuario no ACTIVO → mismo mensaje genérico (no filtra existencia).
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

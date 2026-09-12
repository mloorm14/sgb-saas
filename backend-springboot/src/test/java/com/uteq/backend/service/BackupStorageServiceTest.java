package com.uteq.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadYDownload_withoutR2_guardaFileLocalSanitizado() throws Exception {
        BackupStorageService service = new BackupStorageService(null);
        ReflectionTestUtils.setField(service, "storageUrl", tempDir.toString());

        service.upload("backups/manual.zip", "contenido".getBytes());

        Path esperado = tempDir.resolve("backups_manual.zip");
        assertThat(Files.exists(esperado)).isTrue();
        assertThat(service.download("backups/manual.zip")).isEqualTo("contenido".getBytes());
    }

    @Test
    void uploadYDownload_withKeyTextPlain_cifraYDescifraLocalmente() throws Exception {
        BackupStorageService service = new BackupStorageService(null);
        ReflectionTestUtils.setField(service, "storageUrl", tempDir.toString());
        ReflectionTestUtils.setField(service, "encryptionKey", "clave-de-prueba-para-tests");

        byte[] original = "respaldo sensible".getBytes();
        service.upload("backups/cifrado.zip", original);

        byte[] almacenado = Files.readAllBytes(tempDir.resolve("backups_cifrado.zip"));
        assertThat(almacenado).isNotEqualTo(original);
        assertThat(service.download("backups/cifrado.zip")).isEqualTo(original);
    }

    @Test
    void uploadYDownload_withKeyBase64Valida_cifraYDescifraLocalmente() {
        BackupStorageService service = new BackupStorageService(null);
        ReflectionTestUtils.setField(service, "storageUrl", tempDir.toString());
        ReflectionTestUtils.setField(service, "encryptionKey",
                Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes()));

        service.upload("backup.zip", "abc".getBytes());

        assertThat(service.download("backup.zip")).isEqualTo("abc".getBytes());
    }

    @Test
    void delete_withoutR2_eliminaFileLocalSiExiste() throws Exception {
        BackupStorageService service = new BackupStorageService(null);
        ReflectionTestUtils.setField(service, "storageUrl", tempDir.toString());
        Path file = tempDir.resolve("backup.zip");
        Files.write(file, "abc".getBytes());

        service.delete("backup.zip");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void download_cuandoFileNotExiste_envuelveIOException() {
        BackupStorageService service = new BackupStorageService(null);
        ReflectionTestUtils.setField(service, "storageUrl", tempDir.toString());

        assertThatThrownBy(() -> service.download("faltante.zip"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo leer respaldo local");
    }
}

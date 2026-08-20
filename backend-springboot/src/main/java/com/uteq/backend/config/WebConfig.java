package com.uteq.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Sirve el SPA de Angular desde este mismo origen (fix de sesión 2026-08:
 * el backend y el frontend dejaron de ser dos subdominios separados de
 * Render, lo que rompía la cookie HttpOnly refreshToken -- onrender.com
 * está en la Public Suffix List, así que cada subdominio cuenta como un
 * "sitio" distinto y los navegadores modernos bloquean la cookie).
 *
 * La raíz de la aplicación es classpath:/static (ahí copia el build de
 * Angular el pipeline de despliegue). Las rutas del router de la SPA
 * (p.ej. /libros) no existen como archivos: el resolver las redirige a
 * index.html. Los assets reales (index.html, *.js, *.css, imágenes) se
 * sirven tal cual, y los controllers (todo bajo /api/**) tienen prioridad
 * sobre este manejador, así que la API no se ve afectada.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // La superficie de la API conserva su contrato: una ruta
                        // /api/** sin mapeo responde 404, nunca el index.html
                        // del SPA (regresión cubierta por
                        // PublicoLibroControllerTest#otrosDominios_noExistenEnLaSuperficiePublica).
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        if (resourcePath.isEmpty()) {
                            return new ClassPathResource("static/index.html");
                        }
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        return new ClassPathResource("static/index.html");
                    }
                });
    }
}
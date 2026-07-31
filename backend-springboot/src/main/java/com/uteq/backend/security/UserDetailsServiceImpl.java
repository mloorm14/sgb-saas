package com.uteq.backend.security;

import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // estados_usuario.nombre que restringen el login. BLOQUEADO_POR_MULTA
    // se modela como cuenta bloqueada (accountLocked=true -> isAccountNonLocked()
    // = false, el hook de Spring Security pensado para restricciones
    // reversibles/temporales); INACTIVO y PENDIENTE_VERIFICACION como
    // cuenta deshabilitada (disabled=true -> isEnabled()=false). Con esto
    // Spring Security ya rechaza el login (LockedException / DisabledException)
    // sin necesidad de lógica custom en AuthService.
    private static final String ESTADO_BLOQUEADO_POR_MULTA = "BLOQUEADO_POR_MULTA";
    private static final Set<String> ESTADOS_DESHABILITADOS = Set.of("INACTIVO", "PENDIENTE_VERIFICACION");

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con correo: " + correo));

        String estadoNombre = usuario.getEstado().getNombre();

        // roles.nombre en la BD no lleva prefijo ("LECTOR", no "ROLE_LECTOR").
        // User.builder().roles(...) de Spring Security antepone "ROLE_"
        // automáticamente -- justo lo que esperan los @PreAuthorize
        // ("hasAnyRole('LECTOR', ...)") ya existentes en LibroController,
        // que también comparan anteponiendo "ROLE_" a lo que reciben.
        // Ambos lados deben coincidir en anteponer el prefijo una sola vez.
        String[] roles = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .toArray(String[]::new);

        return User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getPasswordHash())
                .roles(roles)
                .accountLocked(ESTADO_BLOQUEADO_POR_MULTA.equals(estadoNombre))
                .disabled(ESTADOS_DESHABILITADOS.contains(estadoNombre))
                .build();
    }
}

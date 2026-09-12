package com.uteq.backend.security;

import com.uteq.backend.entity.Role;
import com.uteq.backend.repository.UserRepository;
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

    private final UserRepository userRepository;

    @Override
    /**
     * Loads User Details.
     *
     * @param email text value used to scope this User Details
     * @return User Details reflecting the state after the operation
     * @throws UsernameNotFoundException when the User Details cannot be processed with the given input
     */
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        com.uteq.backend.entity.User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con correo: " + email));

        String statusName = user.getStatus().getName();

        // roles.nombre en la BD no lleva prefijo ("LECTOR", no "ROLE_LECTOR").
        // User.builder().roles(...) de Spring Security antepone "ROLE_"
        // automáticamente -- justo lo que esperan los @PreAuthorize
        // ("hasAnyRole('LECTOR', ...)") ya existentes en LibroController,
        // que también comparan anteponiendo "ROLE_" a lo que reciben.
        // Ambos lados deben coincidir en anteponer el prefijo una sola vez.
        String[] roles = user.getRoles().stream()
                .map(Role::getName)
                .toArray(String[]::new);

        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles(roles)
                .accountLocked(ESTADO_BLOQUEADO_POR_MULTA.equals(statusName))
                .disabled(ESTADOS_DESHABILITADOS.contains(statusName))
                .build();
    }
}

package com.phishguard.demo.security;

import com.phishguard.demo.model.Usuario;
import com.phishguard.demo.repository.UsuarioRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepo;

    public JwtFilter(JwtService jwtService, UsuarioRepository usuarioRepo) {
        this.jwtService  = jwtService;
        this.usuarioRepo = usuarioRepo;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtService.validar(token)) {
                String email = jwtService.extrairEmail(token);
                Optional<Usuario> usuario = usuarioRepo.findByEmail(email);

                if (usuario.isPresent()) {
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                            usuario.get(), null, List.of()
                        );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
package com.phishguard.demo.controller;

import com.phishguard.demo.model.Usuario;
import com.phishguard.demo.repository.UsuarioRepository;
import com.phishguard.demo.security.JwtService;
import com.phishguard.demo.service.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepo;
    private final JwtService        jwtService;
    private final GmailService      gmailService;

    public AuthController(UsuarioRepository usuarioRepo,
                          JwtService jwtService,
                          GmailService gmailService) {
        this.usuarioRepo = usuarioRepo;
        this.jwtService  = jwtService;
        this.gmailService = gmailService;
    }

    /**
     * Recebe o código OAuth2 do Google, salva o token no banco,
     * cria/atualiza o usuário e retorna um JWT.
     */
    @PostMapping("/google")
    public ResponseEntity<?> loginComGoogle(@RequestBody Map<String, String> body) {
        try {
            String code = body.get("code");
            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Código OAuth2 não informado"));
            }

            // Troca o código pelo token e pega info do usuário
            GmailService.TokenInfo info = gmailService.trocarCodigoPorToken(code);

            // Cria ou atualiza usuário no banco
            Usuario usuario = usuarioRepo.findByEmail(info.email())
                .orElse(new Usuario(info.email(), info.nome()));

            usuario.setGmailAccessToken(info.accessToken());
            usuario.setGmailRefreshToken(info.refreshToken());
            usuario.setTokenExpiracao(info.expiracao());
            usuarioRepo.save(usuario);

            // Gera JWT
            String jwt = jwtService.gerarToken(info.email());

            return ResponseEntity.ok(Map.of(
                "token", jwt,
                "email", info.email(),
                "nome",  info.nome()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Retorna info do usuário logado pelo JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestAttribute(required = false) Usuario usuarioLogado) {

        // Pega o usuário do SecurityContext
        var auth = org.springframework.security.core.context
                      .SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof Usuario u)) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        return ResponseEntity.ok(Map.of(
            "email",     u.getEmail(),
            "nome",      u.getNome(),
            "criadoEm",  u.getCriadoEm()
        ));
    }
    @PostMapping("/dev/token")
    public ResponseEntity<?> devToken(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Email obrigatório"));
        }
        if (!usuarioRepo.existsByEmail(email)) {
            usuarioRepo.save(new Usuario(email, email.split("@")[0]));
        }
        String jwt = jwtService.gerarToken(email);
        return ResponseEntity.ok(Map.of("token", jwt));
    }
}
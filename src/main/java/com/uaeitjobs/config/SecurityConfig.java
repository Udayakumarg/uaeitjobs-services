package com.uaeitjobs.config;

import com.uaeitjobs.security.CustomAccessDeniedHandler;
import com.uaeitjobs.security.JwtAuthenticationEntryPoint;
import com.uaeitjobs.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint entryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    @Value("${app.frontend-domain}")
    private String frontendDomain;   // comma-separated for multi-origin support

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/jobs/**", "/api/v1/skills/**", "/api/v1/locations", "/api/v1/stats", "/api/v1/seo/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/hr/**", "/api/v1/subscriptions/**", "/api/v1/linkedin-import").hasRole("HR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/jobs").hasRole("HR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/jobs/**").hasRole("HR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/jobs/**").hasRole("HR")
                        .requestMatchers("/api/v1/job-seeker/**", "/api/v1/saved-jobs/**").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/applications/**").hasRole("HR")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = new java.util.ArrayList<>(List.of("http://localhost:3000", "http://localhost:5173", "http://127.0.0.1:5173"));
        for (String origin : frontendDomain.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) origins.add(trimmed);
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

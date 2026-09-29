package com.payflow.config;

import com.payflow.auth.JwtService;
import com.payflow.common.ApiErrorHttp;
import com.payflow.common.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String AUTH_ERROR = "payflow.auth.error";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, ApiErrorHttp errors, PayflowProperties properties)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource(properties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            if ("expired".equals(request.getAttribute(AUTH_ERROR))) {
                                errors.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_EXPIRED, "Access token has expired");
                            } else {
                                errors.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication is required");
                            }
                        })
                        .accessDeniedHandler((request, response, ex) ->
                                errors.write(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have access to this resource")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/webhooks/gateway").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource corsSource(PayflowProperties properties) {
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOriginPatterns(properties.getCors().getAllowedOriginPatterns());
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
            configuration.setAllowCredentials(false);
            return configuration;
        };
    }

    static final class JwtFilter extends OncePerRequestFilter {
        private final JwtService jwt;

        JwtFilter(JwtService jwt) {
            this.jwt = jwt;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                try {
                    var principal = jwt.parse(token);
                    var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (ExpiredJwtException ex) {
                    request.setAttribute(AUTH_ERROR, "expired");
                } catch (JwtException | IllegalArgumentException ex) {
                    request.setAttribute(AUTH_ERROR, "invalid");
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}

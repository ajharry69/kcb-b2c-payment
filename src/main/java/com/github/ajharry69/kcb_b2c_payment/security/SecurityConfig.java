package com.github.ajharry69.kcb_b2c_payment.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    /*@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }*/

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JWTAuthConverter converter) throws Exception {
        http
                .authorizeHttpRequests(authorizeRequests ->
                                authorizeRequests.anyRequest()
                                        .permitAll()
                        /*authorizeRequests
                                .requestMatchers(antMatcher("/h2-console/**")).permitAll()
                                .requestMatchers(antMatcher("/v3/api-docs/**"),
                                        antMatcher("/swagger-ui/**"),
                                        antMatcher("/swagger-ui.html")).permitAll()
                                .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/payments")).authenticated()
                                .requestMatchers(antMatcher(HttpMethod.GET, "/api/v1/payments/**")).authenticated()
                                .anyRequest()
                                .denyAll()*/
                )
                /*.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> {
                    jwtConfigurer.decoder(jwtDecoder());
                    jwtConfigurer.jwtAuthenticationConverter(converter);
                }))*/
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }
}


package com.gte619n.healthfitness;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// IMPL-00 boot config: permit all requests so the Hello endpoint is reachable
// over plain HTTP. Replaced in IMPL-XX (login flow) with real OAuth2 login +
// per-endpoint authorization rules. Until then, no real data is exposed —
// /api/hello returns a static greeting and Firestore is unused.
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable());
        return http.build();
    }
}

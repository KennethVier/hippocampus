package com.hippocampus.identity.infrastructure.security;

import java.time.Clock;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import com.hippocampus.identity.infrastructure.web.JsonLoginAuthenticationFilter;
import com.hippocampus.identity.infrastructure.web.SecurityProblemWriter;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean LoginRateLimiter loginRateLimiter(LoginRateLimitProperties properties) {
        return new LoginRateLimiter(properties, Clock.systemUTC());
    }
    @Bean AuthenticationManager authenticationManager(DatabaseAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }
    @Bean HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean ChangeSessionIdAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }
    @Bean HttpSessionCsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }
    @Bean CsrfAuthenticationStrategy csrfAuthenticationStrategy(CsrfTokenRepository repository) {
        return new CsrfAuthenticationStrategy(repository);
    }
    @Bean CompositeSessionAuthenticationStrategy loginSessionAuthenticationStrategy(
            ChangeSessionIdAuthenticationStrategy sessionStrategy,
            CsrfAuthenticationStrategy csrfStrategy) {
        return new CompositeSessionAuthenticationStrategy(List.of(sessionStrategy, csrfStrategy));
    }
    @Bean AccessDeniedHandler apiAccessDeniedHandler(SecurityProblemWriter problems) {
        return (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                problems.write(request, response, HttpStatus.FORBIDDEN,
                        "CSRF_VALIDATION_FAILED", "CSRF validation failed.");
            } else {
                problems.write(request, response, HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED", "Access is denied.");
            }
        };
    }
    @Bean FilterRegistrationBean<CorrelationIdFilter> correlationFilterOrder(CorrelationIdFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager manager,
            ObjectMapper objectMapper, LoginRateLimiter limiter, SecurityProblemWriter problems,
            HttpSessionSecurityContextRepository contextRepository,
            ChangeSessionIdAuthenticationStrategy sessionStrategy,
            CompositeSessionAuthenticationStrategy loginSessionStrategy,
            CsrfTokenRepository csrfTokenRepository,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        var login = new JsonLoginAuthenticationFilter(manager, objectMapper, limiter, problems);
        login.setSecurityContextRepository(contextRepository);
        login.setSessionAuthenticationStrategy(loginSessionStrategy);
        login.setAuthenticationSuccessHandler((request, response, authentication) -> response.setStatus(204));
        login.setAuthenticationFailureHandler((request, response, exception) -> problems.write(request, response,
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed."));

        http.authenticationManager(manager)
                .securityMatcher("/api/**")
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionStrategy))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) ->
                        problems.write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                                "Authentication is required."))
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .addFilterAt(login, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

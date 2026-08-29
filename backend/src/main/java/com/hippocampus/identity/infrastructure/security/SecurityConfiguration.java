package com.hippocampus.identity.infrastructure.security;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hippocampus.identity.infrastructure.web.JsonLoginAuthenticationFilter;
import com.hippocampus.identity.infrastructure.web.SecurityProblemWriter;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({LoginRateLimitProperties.class, CorsProperties.class})
public class SecurityConfiguration {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String CSRF_PATH = "/api/auth/csrf";
    private static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    private static final List<String> CORS_ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name());
    private static final List<String> CORS_ALLOWED_HEADERS = List.of(
            HttpHeaders.ACCEPT,
            HttpHeaders.CONTENT_TYPE,
            CSRF_HEADER_NAME);

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    LoginRateLimiter loginRateLimiter(LoginRateLimitProperties properties) {
        return new LoginRateLimiter(properties, Clock.systemUTC());
    }

    @Bean
    AuthenticationManager authenticationManager(DatabaseAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    ChangeSessionIdAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName(CSRF_HEADER_NAME);
        return repository;
    }

    @Bean
    CsrfAuthenticationStrategy csrfAuthenticationStrategy(CsrfTokenRepository repository) {
        return new CsrfAuthenticationStrategy(repository);
    }

    @Bean
    CompositeSessionAuthenticationStrategy loginSessionAuthenticationStrategy(
            ChangeSessionIdAuthenticationStrategy sessionStrategy,
            CsrfAuthenticationStrategy csrfStrategy) {
        return new CompositeSessionAuthenticationStrategy(List.of(sessionStrategy, csrfStrategy));
    }

    @Bean
    AccessDeniedHandler apiAccessDeniedHandler(SecurityProblemWriter problems) {
        return (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                problems.write(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "CSRF_VALIDATION_FAILED",
                        "CSRF validation failed.");
                return;
            }
            problems.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "Access is denied.");
        };
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(CORS_ALLOWED_METHODS);
        configuration.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER_NAME));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationFilterOrder(CorrelationIdFilter filter) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager manager,
            ObjectMapper objectMapper,
            LoginRateLimiter limiter,
            SecurityProblemWriter problems,
            HttpSessionSecurityContextRepository contextRepository,
            ChangeSessionIdAuthenticationStrategy sessionStrategy,
            CompositeSessionAuthenticationStrategy loginSessionStrategy,
            CsrfTokenRepository csrfTokenRepository,
            UrlBasedCorsConfigurationSource corsConfigurationSource,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        JsonLoginAuthenticationFilter loginFilter = new JsonLoginAuthenticationFilter(
                manager,
                objectMapper,
                limiter,
                problems);
        loginFilter.setSecurityContextRepository(contextRepository);
        loginFilter.setSessionAuthenticationStrategy(loginSessionStrategy);
        loginFilter.setAuthenticationSuccessHandler(
                (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()));
        loginFilter.setAuthenticationFailureHandler(
                (request, response, exception) -> problems.write(
                        request,
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_FAILED",
                        "Authentication failed."));

        http.authenticationManager(manager)
                .securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionStrategy))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(LOGIN_PATH, LOGOUT_PATH, CSRF_PATH).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required."))
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_PATH)
                        .logoutSuccessHandler(
                                (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

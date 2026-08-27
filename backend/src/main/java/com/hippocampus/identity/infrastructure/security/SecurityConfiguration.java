package com.hippocampus.identity.infrastructure.security;

import java.time.Clock;

import tools.jackson.databind.ObjectMapper;
import com.hippocampus.identity.infrastructure.web.JsonLoginAuthenticationFilter;
import com.hippocampus.identity.infrastructure.web.SecurityProblemWriter;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
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
    @Bean FilterRegistrationBean<CorrelationIdFilter> correlationFilterOrder(CorrelationIdFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager manager,
            ObjectMapper objectMapper, LoginRateLimiter limiter, SecurityProblemWriter problems,
            HttpSessionSecurityContextRepository contextRepository,
            ChangeSessionIdAuthenticationStrategy sessionStrategy) throws Exception {
        var login = new JsonLoginAuthenticationFilter(manager, objectMapper, limiter, problems);
        login.setSecurityContextRepository(contextRepository);
        login.setSessionAuthenticationStrategy(sessionStrategy);
        login.setAuthenticationSuccessHandler((request, response, authentication) -> response.setStatus(204));
        login.setAuthenticationFailureHandler((request, response, exception) -> problems.write(request, response,
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed."));

        http.authenticationManager(manager)
                .securityMatcher("/api/**")
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionStrategy))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) ->
                        problems.write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                                "Authentication is required.")))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .addFilterAt(login, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

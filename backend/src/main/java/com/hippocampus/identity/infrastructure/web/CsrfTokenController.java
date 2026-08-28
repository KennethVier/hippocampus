package com.hippocampus.identity.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.identity.api.CsrfTokenResponse;

@RestController
public final class CsrfTokenController {
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<CsrfTokenResponse> token(HttpServletRequest request) {
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(token.getToken()));
    }
}

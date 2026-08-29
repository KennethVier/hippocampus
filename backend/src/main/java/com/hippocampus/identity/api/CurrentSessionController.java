package com.hippocampus.identity.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.identity.application.CurrentSession;
import com.hippocampus.identity.application.GetCurrentSession;

@RestController
@RequestMapping("/api/auth")
public final class CurrentSessionController {

    private final GetCurrentSession getCurrentSession;

    public CurrentSessionController(GetCurrentSession getCurrentSession) {
        this.getCurrentSession = getCurrentSession;
    }

    @GetMapping("/me")
    CurrentSessionResponse currentSession()
            {
        CurrentSession session = getCurrentSession.execute();
        return new CurrentSessionResponse(session.userId());
    }
}

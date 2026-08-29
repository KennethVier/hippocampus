package com.hippocampus.identity.application;

import org.springframework.stereotype.Service;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;

@Service
public final class GetCurrentSession {

    private final CurrentUser currentUser;

    public GetCurrentSession(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public CurrentSession execute() {
        AuthenticatedUser authenticatedUser = currentUser.authenticatedUser();
        return new CurrentSession(authenticatedUser.userId());
    }
}

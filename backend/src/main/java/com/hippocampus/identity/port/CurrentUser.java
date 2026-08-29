package com.hippocampus.identity.port;

import com.hippocampus.identity.domain.AuthenticatedUser;

/**
 * Provides the authenticated ownership root to application code without exposing
 * Spring Security or client-controlled identity.
 */
public interface CurrentUser {

    AuthenticatedUser authenticatedUser();
}

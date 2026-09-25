package com.utp.assistant.auth.config;

import java.io.IOException;

import com.utp.assistant.auth.service.GoogleAutomationAccount;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Tras el login con Google registra la cuenta para la automatización y redirige a login-success-url
 * (mismo comportamiento que defaultSuccessUrl(url, true)).
 */
@Component
public class GoogleLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleAutomationAccount automationAccount;

    public GoogleLoginSuccessHandler(GoogleAutomationAccount automationAccount,
                                     @Value("${app.security.login-success-url}") String loginSuccessUrl) {
        super(loginSuccessUrl);
        setAlwaysUseDefaultTargetUrl(true);
        this.automationAccount = automationAccount;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        automationAccount.register(authentication);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}

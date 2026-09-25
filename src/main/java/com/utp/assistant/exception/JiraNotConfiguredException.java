package com.utp.assistant.exception;

/** Faltan JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN o JIRA_PROJECT_KEY (HTTP 503). */
public class JiraNotConfiguredException extends RuntimeException {

    public JiraNotConfiguredException(String message) {
        super(message);
    }
}

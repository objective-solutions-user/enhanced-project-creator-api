package br.objective.jira.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;

public class LoggedUser {
	public static ApplicationUser get() {
		return ComponentAccessor.getJiraAuthenticationContext().getUser();
	}
}

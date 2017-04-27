package br.objective.jira.rest;

import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRole;

public class UserProjectRole {
	public String projectKey;
	public String name;
	public Long id;
	
	public static UserProjectRole fromRole(Project project, ProjectRole pr) {
		UserProjectRole userRole = new UserProjectRole();
		userRole.projectKey = project.getKey();
		userRole.id = pr.getId();
		userRole.name = pr.getName();
		return userRole;
	}
}

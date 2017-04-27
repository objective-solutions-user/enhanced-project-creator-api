package br.objective.jira.rest;

import java.util.ArrayList;
import java.util.List;

import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.user.ApplicationUser;

public class UserDetail {
	public UserData userData;
	public List<UserProjectRole> roles = new ArrayList<UserProjectRole>();
	
	public UserDetail(){}
	
	public static UserDetail fromUser(ApplicationUser user) {
		UserDetail detail = new UserDetail();
		detail.userData = UserData.fromJiraUser(user);
		return detail;
	}

	public void addRole(Project project, ProjectRole pr) {
		roles.add(UserProjectRole.fromRole(project, pr));
	}

}

package br.objective.jira.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.AssigneeTypes;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;

public class ProjectCreationWrapper {

	static Project createBasicProject(ProjectData data, ProjectBuilderResponse response, ApplicationUser lead) {
			Project newProject = ComponentAccessor.getProjectManager().createProject(data.name,
	                data.key,
	                data.description,
	                ComponentAccessor.getJiraAuthenticationContext().getUser().getKey(),
	                "",
	                AssigneeTypes.PROJECT_LEAD,
	                null);
			
		    response.idOfCreatedProject = newProject.getId();
			return newProject;
		}

}

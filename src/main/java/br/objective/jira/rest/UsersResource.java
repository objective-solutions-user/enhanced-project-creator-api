package br.objective.jira.rest;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;

@Path("/users")
public class UsersResource {
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<UserData> listUsers() {
		Collection<ApplicationUser> allUsers = ComponentAccessor.getUserManager().getAllApplicationUsers();
		List<UserData> result = new LinkedList<UserData>();	
		for (ApplicationUser user : allUsers) 
			result.add(UserData.fromJiraUser(user));
		
		return result;
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{username}")
	public Response getUserRoles(@PathParam("username") String username) {
		ProjectRoleManager roleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
		ApplicationUser user = ComponentAccessor.getUserManager().getUserByKey(username);
		if (user == null) 
			return Response.status(Response.Status.NOT_FOUND).build();
		
		List<Project> projectObjects = ComponentAccessor.getProjectManager().getProjectObjects();
		Collection<ProjectRole> projectRoles = roleManager.getProjectRoles();
		
		boolean loggedUserIsQueriedUser = user.getKey().equals(LoggedUser.get().getKey());
		UserDetail ud = UserDetail.fromUser(user);
		for (Project project : projectObjects) {
			if (!hasPermissionToQueryRole(loggedUserIsQueriedUser, project))
				continue;
			
			for (ProjectRole pr : projectRoles) 
				if (roleManager.isUserInProjectRole(user, pr, project)) 
					ud.addRole(project, pr);
		}
		return Response.ok(ud).build();
	}


	public boolean hasPermissionToQueryRole(boolean loggedUserIsQueriedUser, Project project) {
		// the user can see his own roles
		if (loggedUserIsQueriedUser)
			return true;
		
		// project administrators can query the roles of a user in his project
		return ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, LoggedUser.get());
	}
}

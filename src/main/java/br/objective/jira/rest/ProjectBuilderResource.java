package br.objective.jira.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.atlassian.jira.bc.projectroles.ProjectRoleService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldUtils;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigManager;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.FieldConfigurationScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.security.roles.actor.AbstractRoleActor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;

@Path("/project")
public class ProjectBuilderResource {

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createProject(ProjectData data) {
		ProjectBuilderResponse response = createNewProject(data);
		if (response.success)
			return Response.ok(response).build();
		return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
	}
	
	private ProjectBuilderResponse createNewProject(ProjectData data)
	{
		ProjectBuilderResponse response = new ProjectBuilderResponse();
	    ApplicationUser lead = ComponentAccessor.getUserManager().getUserByKey(data.lead);
	    if (lead == null) 
	    	return response.withError("Lead id " + data.lead + " not found.");
	    
	    final Project newProject;
	    try {

	    	Project projectByKey = getProjectByKey(data.key);
	    	if (projectByKey != null) {
	    		response.idOfCreatedProject = projectByKey.getId();
	    		return response;
	    	}
	    	newProject = createBasicProject(data, response, lead);
	    }catch(Exception e) {
	    	return response.withError("Failed to created project", e);
	    }
	
	    String currentAction = "";
	    try {
		    currentAction = "associating IssueTypeScheme";
		    associateIssueTypeScheme(data, newProject);
		    
	    	currentAction = "associating WorkflowScheme";
	    	associateWorkflowScheme(data, newProject);
	    	
		    currentAction = "associating IssueTypeScreenScheme";
	    	associateIssueTypeScreenScheme(data, newProject);
	    	
		    currentAction = "associating FieldConfigurationScheme";
		    associateFieldConfigurationScheme(data, newProject);
		    
	    	currentAction = "associating NotificationScheme";
		    associateNotificationScheme(data, newProject);
		    
	    	currentAction = "associating PermissionScheme"; 
		    associatePermissionScheme(data, newProject);
		    	    	
		    currentAction = "associating CustomFields";
		    associateCustomFields(data, newProject);
		    
		    currentAction = "associating users in roles";
		    associateUsersInRoles(data, newProject);
	    }
	    catch(Exception e) {
	    	response.withError("An error ocurred when " + currentAction, e);
	    	try {
	    		ComponentAccessor.getProjectManager().removeProject(newProject);
	    		response.withError("Project not created.");
	    		response.idOfCreatedProject = null;
	    	}catch(Exception e1) {
	    		return response.withError("Project was created, but with errors. Attempt to remove project failed.", e);
	    	}
	    }
	    return response;
	}

	private void associateUsersInRoles(ProjectData data, Project newProject) {
		ProjectRoleManager roleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
		ProjectRoleService roleService = ComponentAccessor.getComponentOfType(ProjectRoleService.class);
		
		StringBuffer errors = new StringBuffer();
		for (Entry<String, List<String>> projectRole : data.userInRoles.entrySet()) {
			ProjectRole aRole = roleManager.getProjectRole(projectRole.getKey());
			if (aRole == null) {
				errors.append("Project role " + projectRole.getKey() + " not found\n");
				continue;
			}
			ErrorCollection errorCollection = new SimpleErrorCollection();
			roleService.addActorsToProjectRole(projectRole.getValue(), aRole, newProject, AbstractRoleActor.USER_ROLE_ACTOR_TYPE, errorCollection);
			
			if (errorCollection.hasAnyErrors()) 
				for (String errorMessage : errorCollection.getErrorMessages()) 
					errors.append(errorMessage+"\n");
				
		}
	}

	private Project createBasicProject(ProjectData data, ProjectBuilderResponse response, ApplicationUser lead) {
		return ProjectCreationWrapper.createBasicProject(data, response, lead);
	}

	private Project getProjectByKey(String key) {
		return ComponentAccessor.getProjectManager().getProjectByCurrentKey(key);
	}

	private void associatePermissionScheme(ProjectData data, Project newProject) {
		if (data.permissionScheme != null) {
	    	Scheme permissionScheme = ComponentAccessor.getPermissionSchemeManager().getSchemeObject(data.permissionScheme);
	    	if (permissionScheme == null)
	    		throw new IllegalArgumentException("PermissionScheme id " + data.permissionScheme + " not found");
	    	
			ComponentAccessor.getPermissionSchemeManager().addSchemeToProject(newProject, permissionScheme);
	    }
	    else
	    	ComponentAccessor.getPermissionSchemeManager().addDefaultSchemeToProject(newProject);
	}

	private void associateCustomFields(ProjectData data, Project newProject) {
		if (data.customFields == null)
			return;
		
		CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
		FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
		FieldConfigManager fieldConfigManager = ComponentAccessor.getComponent(FieldConfigManager.class);
		
		for (Long cfId : data.customFields) {
			CustomField customField = customFieldManager.getCustomFieldObject(cfId);
			if (customField == null) 
				throw new IllegalArgumentException("Custom Field id " + cfId + " not found");
			
            for (FieldConfigScheme fieldConfigScheme : fieldConfigSchemeManager.getConfigSchemesForField(customField)){
                Long fcsId = fieldConfigScheme.getId();
                FieldConfigScheme cfConfigScheme = fieldConfigSchemeManager.getFieldConfigScheme(fcsId);
                FieldConfigScheme.Builder cfSchemeBuilder = new FieldConfigScheme.Builder(cfConfigScheme);
                FieldConfig config = fieldConfigManager.getFieldConfig(fcsId);

                HashMap<String, FieldConfig> configs = new HashMap<String, FieldConfig>();

                for (String issueTypeId : fieldConfigScheme.getAssociatedIssueTypeIds())
                    configs.put(issueTypeId, config);

                cfSchemeBuilder.setConfigs(configs);
                cfConfigScheme = cfSchemeBuilder.toFieldConfigScheme();

                List<Long> projectIdList = fieldConfigScheme.getAssociatedProjectIds();
                projectIdList.add(newProject.getId());

                List<JiraContextNode> contexts = CustomFieldUtils.buildJiraIssueContexts(false,
                        projectIdList.toArray(new Long[projectIdList.size()]),
                        ComponentAccessor.getProjectManager());

                fieldConfigSchemeManager.updateFieldConfigScheme
                    (cfConfigScheme, contexts, customFieldManager.getCustomFieldObject(customField.getId()));
            }
        }
        customFieldManager.refresh();
	}

	private void associateIssueTypeScheme(ProjectData data, Project newProject) {
		Long issueTypeSchemeId = data.issueTypeScheme;
		if (issueTypeSchemeId == null)
			return;
		
		FieldConfigScheme issueTypeScheme = findIssueTypeSchemeGivenId(issueTypeSchemeId);
		if (issueTypeScheme == null)
			throw new IllegalArgumentException("IssueTypeScheme id " + issueTypeSchemeId + " not found.");
		
	    List<JiraContextNode> jiraIssueContexts = CustomFieldUtils.buildJiraIssueContexts(true, 
	    		new Long[]{newProject.getId()}, 
	    		ComponentAccessor.getProjectManager());
	    
	    FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
	    fieldConfigSchemeManager.updateFieldConfigScheme(issueTypeScheme,
	    		jiraIssueContexts,
	    		ComponentAccessor.getFieldManager().getConfigurableField(IssueFieldConstants.ISSUE_TYPE));
	}

	private FieldConfigScheme findIssueTypeSchemeGivenId(Long issueTypeSchemeId) {
		List<FieldConfigScheme> allSchemes = ComponentAccessor.getIssueTypeSchemeManager().getAllSchemes();
		
		for (FieldConfigScheme aScheme : allSchemes) {
			if (aScheme.getId().equals(issueTypeSchemeId)) 
				return aScheme;
		}
		return null;
	}

	private void associateFieldConfigurationScheme(ProjectData data, Project newProject) {
		if (data.fieldConfigurationScheme != null) {
			FieldConfigurationScheme fieldConfigurationScheme = ComponentAccessor.getFieldLayoutManager().getFieldConfigurationScheme(data.fieldConfigurationScheme);
			if (fieldConfigurationScheme == null)
				throw new IllegalArgumentException("FieldConfigurationSchema with id " + data.fieldConfigurationScheme + " not found.");
			
			ComponentAccessor.getFieldLayoutManager().addSchemeAssociation(newProject, data.fieldConfigurationScheme);
		}
	}

	private void associateNotificationScheme(ProjectData data, Project newProject) {
		if (data.notificationScheme == null)
	    	ComponentAccessor.getNotificationSchemeManager().addDefaultSchemeToProject(newProject);
	    else {
	    	Scheme notificationScheme = ComponentAccessor.getNotificationSchemeManager().getSchemeObject(data.notificationScheme);
	    	if (notificationScheme == null)
	    		throw new IllegalArgumentException("NotificationScheme id " + data.notificationScheme + " not found");
	    	ComponentAccessor.getNotificationSchemeManager().addSchemeToProject(newProject, notificationScheme);
	    }
	}

	private void associateWorkflowScheme(ProjectData data, Project newProject) {
		if (data.workflowScheme == null)
	    	ComponentAccessor.getWorkflowSchemeManager().addDefaultSchemeToProject(newProject);
	    else {
	    	Scheme workflowScheme = ComponentAccessor.getWorkflowSchemeManager().getSchemeObject(data.workflowScheme);
	    	if (workflowScheme == null)
	    		throw new IllegalArgumentException("WorkflowScheme id " + data.workflowScheme + " not found");
	    	ComponentAccessor.getWorkflowSchemeManager().addSchemeToProject(newProject, workflowScheme);
	    }
	}

	private void associateIssueTypeScreenScheme(ProjectData data, Project newProject) {
		IssueTypeScreenScheme issueTypeScreenScheme;
	    if (data.issueTypeScreenScheme == null)
	    	issueTypeScreenScheme = ComponentAccessor.getIssueTypeScreenSchemeManager().getDefaultScheme();
	    else
	    	issueTypeScreenScheme = ComponentAccessor.getIssueTypeScreenSchemeManager().getIssueTypeScreenScheme(data.issueTypeScreenScheme);
	    
	    ComponentAccessor.getIssueTypeScreenSchemeManager().addSchemeAssociation(newProject, issueTypeScreenScheme);
	}
}

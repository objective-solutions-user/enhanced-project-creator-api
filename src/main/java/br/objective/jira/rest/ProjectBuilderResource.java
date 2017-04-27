package br.objective.jira.rest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.atlassian.jira.notification.NotificationSchemeManager;
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
	private static final Logger logger = LoggerFactory.getLogger(ProjectBuilderResource.class);
	

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
		logger.debug("Request to create new project received " + data);
		
		ProjectBuilderResponse response = new ProjectBuilderResponse();
	    
	    final Project newProject;
	    try {
	    	Project projectByKey = getProjectByKey(data.key);
	    	if (projectByKey != null) {
	    		logger.debug("Requested project " + data.key + " already exists. Nothing to do");
	    		response.idOfCreatedProject = projectByKey.getId();
	    		return response;
	    	}
		    ApplicationUser lead = ComponentAccessor.getUserManager().getUserByKey(data.lead);
		    if (lead == null) 
		    	return response.withError("Lead id " + data.lead + " not found.");

	    	newProject = createBasicProject(data, response, lead);
	    }catch(Exception e) {
	    	return response.withError("Failed to created project", e);
	    }
	
	    LinkedList<String> actionLog = new LinkedList<String>();
	    try {
	    	actionLog.add("associating WorkflowScheme");
	    	associateWorkflowScheme(data, newProject);
	    	
	    	actionLog.add("associating IssueTypeScreenScheme");
	    	associateIssueTypeScreenScheme(data, newProject);
	    	
		    actionLog.add("associating FieldConfigurationScheme");
		    associateFieldConfigurationScheme(data, newProject);
		    
	    	actionLog.add("associating NotificationScheme");
		    associateNotificationScheme(data, newProject);
		    
	    	actionLog.add("associating PermissionScheme"); 
		    associatePermissionScheme(data, newProject);
		    	    	
		    actionLog.add("associating users in roles");
		    response.addWarnings(associateUsersInRoles(data, newProject));
		    
		    actionLog.add("associating CustomFields");
		    associateCustomFields(data, newProject);
		    
		    actionLog.add("associating IssueTypeScheme");
		    associateIssueTypeScheme(data, newProject);
	    }
	    catch(Exception e) {
	    	response.withError("An error ocurred when " + actionLog.getLast(), e);
	    	try {
	    		response.withError("Project created, but it is incomplete.");
	    		actionLog.removeLast();
	    		response.withError("Actions that have been performed succesfully" + StringUtils.join(actionLog,","));
	    		response.idOfCreatedProject = newProject.getId();
	    	}catch(Exception e1) {
	    		return response.withError("Project was created, but with errors. Attempt to remove project failed.", e);
	    	}
	    }
	    return response;
	}

	private List<String> associateUsersInRoles(ProjectData data, Project newProject) {
		final ProjectRoleManager roleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
		final ProjectRoleService roleService = ComponentAccessor.getComponentOfType(ProjectRoleService.class);
		
		final List<String> warnings = new LinkedList<String>();
		for (Entry<String, List<String>> projectRole : data.userInRoles.entrySet()) {
			ProjectRole aRole = roleManager.getProjectRole(projectRole.getKey());
			if (aRole == null) {
				warnings.add("Project role " + projectRole.getKey() + " not found\n");
				continue;
			}
			Iterator<String> it = projectRole.getValue().iterator();
			while(it.hasNext()) {
				String user = it.next();
				if (ComponentAccessor.getUserManager().getUserByKey(user)==null) {
					warnings.add("User " + user + " was not found, therefore it was not added to the project");
					it.remove();
				}
			}
			ErrorCollection errorCollection = new SimpleErrorCollection();
			roleService.addActorsToProjectRole(projectRole.getValue(), aRole, newProject, AbstractRoleActor.USER_ROLE_ACTOR_TYPE, errorCollection);
			
			if (errorCollection.hasAnyErrors()) 
				for (String errorMessage : errorCollection.getErrorMessages()) 
					warnings.add(errorMessage+"\n");
				
		}
		return warnings;
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
		
		for (CustomFieldData cf : data.customFields) {
			CustomField customField = customFieldManager.getCustomFieldObject(cf.id);
			if (customField == null) 
				throw new IllegalArgumentException("Custom Field id " + cf.id + " not found");
			
			FieldConfigScheme fieldConfigScheme = getFieldConfigSchemeForCustomField(customField, cf.schemeId);
			
			if (fieldConfigScheme == null)
				throw new IllegalArgumentException("Custom Field id " + cf.id + " has no field scheme id " + cf.schemeId);
						
            Long fcsId = fieldConfigScheme.getId();
            
            FieldConfigScheme cfConfigScheme = fieldConfigSchemeManager.getFieldConfigScheme(fcsId);
            FieldConfigScheme.Builder cfSchemeBuilder = new FieldConfigScheme.Builder(cfConfigScheme);
            FieldConfig config = fieldConfigManager.getFieldConfig(fcsId);

            HashMap<String, FieldConfig> configs = new HashMap<String, FieldConfig>();

            for (String issueTypeId : fieldConfigScheme.getAssociatedIssueTypeIds())
                configs.put(issueTypeId, config);

            cfSchemeBuilder.setConfigs(configs);
            cfConfigScheme = cfSchemeBuilder.toFieldConfigScheme();

            LinkedList<Long> projectIdList = new LinkedList<Long>();
            projectIdList.addAll(fieldConfigScheme.getAssociatedProjectIds());
            projectIdList.add(newProject.getId());

            List<JiraContextNode> contexts = CustomFieldUtils.buildJiraIssueContexts(false,
                    projectIdList.toArray(new Long[projectIdList.size()]),
                    ComponentAccessor.getProjectManager());

            fieldConfigSchemeManager.updateFieldConfigScheme
                (cfConfigScheme, contexts, customFieldManager.getCustomFieldObject(customField.getId()));
            
        }
        customFieldManager.refresh();
	}

	private FieldConfigScheme getFieldConfigSchemeForCustomField(CustomField customField, Long schemeId) {
		FieldConfigSchemeManager fieldConfigSchemeManager = ComponentAccessor.getFieldConfigSchemeManager();
		for (FieldConfigScheme fieldConfigScheme : fieldConfigSchemeManager.getConfigSchemesForField(customField))
            if (fieldConfigScheme.getId().equals(schemeId))
            	return fieldConfigScheme;
		
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
		NotificationSchemeManager nsm = ComponentAccessor.getNotificationSchemeManager();
		if (data.notificationScheme == null)
	    	nsm.addDefaultSchemeToProject(newProject);
	    else {
	    	Scheme notificationScheme = nsm.getSchemeObject(data.notificationScheme);
	    	if (notificationScheme == null)
	    		throw new IllegalArgumentException("NotificationScheme id " + data.notificationScheme + " not found");
	    	nsm.addSchemeToProject(newProject, notificationScheme);
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
	
	private void associateIssueTypeScheme(ProjectData data, Project newProject) {
		Long issueTypeSchemeId = data.issueTypeScheme;
		if (issueTypeSchemeId == null)
			return;
		
		FieldConfigScheme issueTypeScheme = findIssueTypeSchemeGivenId(issueTypeSchemeId);
		if (issueTypeScheme == null)
			throw new IllegalArgumentException("IssueTypeScheme id " + issueTypeSchemeId + " not found.");
		
		LinkedList<Long> projects = new LinkedList<Long>(issueTypeScheme.getAssociatedProjectIds());
		projects.add(newProject.getId());
		
	    List<JiraContextNode> jiraIssueContexts = CustomFieldUtils.buildJiraIssueContexts(false, 
	    		projects.toArray(new Long[0]), 
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
}
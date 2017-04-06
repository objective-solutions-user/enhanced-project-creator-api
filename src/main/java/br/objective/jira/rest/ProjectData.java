package br.objective.jira.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProjectData {
	public String name;
	public String key;
	public String description;
	public String projectTypeKey;
	public String projectTemplateKey;
	public String lead;
	public Long permissionScheme;
	public Long issueTypeScreenScheme;
	public Long workflowScheme;
	public Long notificationScheme;
	public Long fieldConfigurationScheme;
	public Long issueTypeScheme;
	public CustomFieldData [] customFields;
	public Map<String, List<String>> userInRoles;
	
	@Override
	public String toString() {
		return "ProjectData [name=" + name + ", key=" + key + ", description=" + description + ", projectTypeKey="
				+ projectTypeKey + ", projectTemplateKey=" + projectTemplateKey + ", lead=" + lead
				+ ", permissionScheme=" + permissionScheme + ", issueTypeScreenScheme=" + issueTypeScreenScheme
				+ ", workflowScheme=" + workflowScheme + ", notificationScheme=" + notificationScheme
				+ ", fieldConfigurationScheme=" + fieldConfigurationScheme + ", issueTypeScheme=" + issueTypeScheme
				+ ", customFields=" + Arrays.toString(customFields) + ", userInRoles=" + userInRoles + "]";
	}
}

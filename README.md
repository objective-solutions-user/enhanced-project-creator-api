# Enhanced Project Builder

This is a jira plugin that exposes a REST api to create a new project with all required schemes and custom field associations.

## Endpoints

### Project creation: /rest/projectbuilder/1.0/project
Expected payload:
```
{
    "key"                      : "Jira project key, no spaces",
    "name"                     : "A project name",
    "description"              : "The project description",
    "projectTypeKey"           : "business",
    "projectTemplateKey"       : "com.atlassian.jira-core-project-templates:jira-core-project-management",
    "lead"                     : "id of the user that will 'lead' the project",
    "userInRoles"              : {
        "Role Name 1"     : ["user1", "user2"],
        "Role Name 2"     : ["user3", "user4"]
    },
    "issueTypeScheme"          : "id of issue type scheme",
    "workflowScheme"           : "id of workflow scheme",
    "issueTypeScreenScheme"    : "id of issue type screen scheme",
    "fieldConfigurationScheme" : "id of field configuration scheme",
    "notificationScheme"       : "id of notification scheme",
    "permissionScheme"         : "id of permission scheme",
    "customFields"             : [ // An array of customfield id x field configuration scheme id 
                                   // which will be associated with the project
        {"id":10000,"schemeId":10101}, 
        {"id":10001,"schemeId":10111}
    ]
}
```

### List users: /rest/projectbuilder/1.0/users
Returns the list of all jira users. Used to allow external systems to create user pickers.

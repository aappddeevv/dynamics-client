---
layout: docs
title: applications
---

The `applications` command allows you to list applications and add/remove roles that can access the application. You often need this if you migrated your app but you need to adjust the roles afterwards.

## Subcommands
* list: List all applications.
* roles: Add/remove roles for a specific application. You add/remove roles by role name, not role id.

## list
List applications in the org.

Examples:
* `dynamicscli applications list -c $CRMCON`

## roles
Change roles by application and role names. If your role or application has a space in it, use proper shell quoting in your CLI.

Examples:
* `dynamicscli applications role add Salesperson MyApp-c $CRMCON`: Add the role to the application.
* `dynamicscli applications role remove Salesperson MyApp -c $CRMCON`: Remove the role from the application.





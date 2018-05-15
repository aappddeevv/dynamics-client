---
layout: docs
title: applications
---

The `applications` command allows you to list applications and add/remove roles
that can access the application. You often need this if you migrated your app
but you need to adjust the roles afterwards.

## Subcommands
* list: List all applications.
* roles: Add/remove roles for a specific application. You add/remove roles by
  role name, not role id. You must use the application unique name and not the
  application display name.

## list
List applications in the org. You can obtain the application name and unique
name using this command.

Examples:
* `dynamicscli applications list`

## roles
Change roles by application and role names. If your role or application has a
space in it, use proper shell quoting in your CLI. The application name must be
the application unique name.

Examples:
* `dynamicscli applications roles add Salesperson new_MyApp`: Add the role to the application.
* `dynamicscli applications roles remove Salesperson new_MyApp`: Remove the role from the application.
* `dynamicscli applications roles add Salesperson,"Sales Manager" new_MyApp`: Add
  the Salesperson and "Sales Manager" roles to the application.

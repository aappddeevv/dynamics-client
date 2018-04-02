---
layout: docs
title: users
---

The `users` command allows you to manag some aspects of users. Users are
typically identified by their email address. "users" are under the "systemuser"
entity.

## Subcommands

* *add-roles*: Add roles to a user, by name.
* *delete-roles*: Delete roles from a user, by name.
* *list*: List users.
* *list-roles*: List roles.
* *user-roles*: List roles for an user given their email address.

## add-roles

Add roles if they are not already present for a user. Roles that are already
present remain without change. Some roles may have "," in them, if so, use
`--roles <rolename>` and quote just the single role. You can add as many
`--roles` parameters as you like instead of using the "," syntax.

Examples:

* `dynamicscli users add-roles someuser@someorg.onmicrosoft.com Salesperson,Salesmanager`: Add Salesperson and Salesmangaer to someuser if they are not already present.

## list

List users and relevant information for them such as their internal email address and their user id.

Examples:

* `dynamicscli users list`: List users and some key information.

## remove-roles

Examples:

* `dynamicscli users remove-roles somuser@someorg.onmicrosoft.com Salesmanager`: Remove the Salesmanager role if it present.

## user-roles

Examples:
* `dynamicscli user users-roles someuser@someorg.onmicrosoft.com`: List roles for "someuser".

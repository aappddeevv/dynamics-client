---
layout: docs
title: workflows
---

The `workflows` command manages workflows. It can execute workflows against a query as well as change a workflow status.


## Subcommands

* change-activation: Change the activation of a workflow.
* execute: Execute a workflow against the results of a OData query. The query must return the primary key of the entity to run the workflow against.
* list: List existing workflows.

You should look for the "Definition" workflows instead of the "Activation" workflow and use the "definition" workflow id in the change-activation command. Deactivating the definition workflow deactivates the activations as well. Each deactivation/activation cycle generates a new "activation" workflow so you may see several "activation" workflows although most of them will be deactivated.


## change-activation

You can activate and deactivate a workflow.

Examples:

* `dynamicscli workflows change-activation true --id abe5b284-9729-4945-8259-4f0a96fddeed`: Activate a workflow.
* `dynamicscli workflows change-activation false --id abe5b284-9729-4945-8259-4f0a96fddeed`: Deactivate a workflow.

## execute

The query to execute the workflow against must return the primary key of the entity. Generally, the query should return the minimal amount of information i.e. just the PK is sufficient. The query may be subject to shell quoting issues so be careful.

Examples:

* `dynamicscli workflows execute 21019c52-6cf6-4cc6-924c-33736818f0f1 '/new_entity?$select=new_entityid' --pk new_entityid`: Execute a workflow against
worklow 210*. The entity is called new_entity and its PK is `new_entityid`.


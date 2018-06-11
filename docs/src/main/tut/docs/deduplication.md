---
layout: docs
title: deduplication
---

# deduplication

`deduplication` allows you to manage some aspects of deduplication rules including import, export and publish/unpublish.

## Subcommands
* [list](#list): List data duplication detection rules.
* [publish](#publish): Publish a duplication detection rule.
* [unpublish](#unpublish): Unpublish a duplicate detection rule.
* [unpublish-all](#unpublish-all): Unpublish all rules.

## list

List existing deduplication rules.

Examples:
* `dynamicscli deduplication list`

## publish

Publish a deduplication rule. Identifiers can be combined with a comma or repeated with `--identifier`.

Examples:
* `dynamicscli deduplication publish xxx-xxx-xxx`: Publish a rule by name.
* `dynamicscli deduplication publish aname`: Publish a rule by name.

## unpublish

Publish a deduplication rule. Identifiers can be combined with a comma or repeated with `--identifier`.

Examples:
* `dynamicscli deduplication unpublish xxx-xxx-xxx`: Unpublish a rule by name.
* `dynamicscli deduplication unpublish aname`: Unpublish a rule by name.

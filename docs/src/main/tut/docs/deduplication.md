---
layout: docs
title: deduplication
---

# deduplication

`deduplication` allows you to manage some aspects of deduplication rules including import, export and publish/unpublish.

## Subcommands
* `list`: List data duplication detection rules.
* `publish`: Publish a duplication detection rule.
* `unpublish`: Unpublish a duplicate detection rule.
* `unpublish-all`: Unpublish all rules.

## list

List existing deduplication rules.

Examples:
* `dynamicscli deduplication list`

## publish

Publish a deduplication rule. Both `--id` and `--name` can be combined in the same command and repeated as necessary.

Examples:
* `dynamicscli deduplication publish --id xxx-xxx-xxx`: Publish a rule by name.
* `dynamicscli deduplication publish --name xxx-xxx-xxx`: Publish a rule by name.

## publish

Publish a deduplication rule. Both `--id` and `--name` can be combined in the same command and repeated as necessary.

Examples:
* `dynamicscli deduplication unpublish --id xxx-xxx-xxx`: Unpublish a rule by name.
* `dynamicscli deduplication unpublish --name xxx-xxx-xxx`: Unpublish a rule by name.

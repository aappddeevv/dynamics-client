---
layout: docs
title: solutions
---

solutions allows you to list, import and export solutions from a Dynamics instance.

## Subcommands
* list: List solutions.
* export: Export a solution.
* delete: Delete a solution
* upload: Upload a solution with an optional upload config file.
* publish-all: Publish all customizations in solutions.

Export and import options come from the web api documentation and are specified in json format.

## export

Export a specfiic solution by its name. The solution is exported as `name_version.zip`.

Documentation for config file parameters is located [here](https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/exportsolution?view=dynamics-ce-odata-9).

Flags:
* --managed: Export as a managed solution.
* --config-file: Use the json config file for the export to specify export options.

Examples:
* `dynamicscli solutions export MySolution`

## publish-all

Publish all changes.

Examples:
* `dynamicscli solutions publish-all`

## upload

Upload and optionally publish its changes. You can specify a solution upload json file which contains various options.

Documentation for config file parameters is located [here](https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/importsolution?view=dynamics-ce-odata-9).

Flags:
* --skip-publishing-workflow: Skip publishing the workflows in a solution. You can add this independently on the command line or specify it in the json configuration file.

Examples:
* `dynamicscli solutions upload MySolution_0.1.0.zip`: Upload using all defaults upload options.
* `dynamicscli solutions upload MySolution_0.1.0.zip --config-file MySolution.config.json`: Upload with additional configuration options.


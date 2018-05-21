---
layout: docs
title: webresources
---

# webresources

The `webresources` command allows you to list, download and upload (one-time and watch) webresources. This method helps you incrementally develop your webresource based solution e.g. a UI written in javascript. You can also delete source maps in a solution that may be leftover from development iterations.

## Subcommands

* upload: Upload web resources. Optionally watch a directory to perform the upload. "Watch" both adds, updates and delete resources from your local disk depending on what happens to the file.
* delete: Delete a specific webresource by id. You can obtain the id from `list`.
* list: List web resources.
* delete-source-maps: Delete .js.map files from a specific solution's set of webresources.

## upload

The only real trick for this command is that --prefix is used to snip off the path so that the starting point of the web resource name becomes the path starting at --prefix with no leading slash.

Web resources are uploaded in parallel if multiple files are changed at once and this can make the output messages confusing at times.

Examples:
* ` $CLI webresources upload --prefix newco_ --unique-solution-name MyNewcoSolution ~/project/dist/newco_ --watch --ignore 'hot' "$@"`: Continuously watch and upload web resources. Skip files that match the glob `*hot*`. Resources are named `newco_/...` and new resources are placed in the `MyNewcoSolution` solution.

## delete

Delete a web resource.

## list

List web resourcse:

Examples:
* `dynamicscli webresources list --filter blah`: List web resources that have the word blah in their name.

## delete-source-maps

Delete sources maps found in a solution.

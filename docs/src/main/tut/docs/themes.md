---
layout: docs
title: themes
---

# themes

themes allows you to manage some aspects of themes including cloning, updating and setting the logo.

## Subcommands
* [list](#list): List themes.
* [copy](#copy): Copy an existing theme to a new one. Optionally merge additional properties into the copy.
* [publish](#publish): Publish a theme.
* [set-logo](#set-logo): Set the logo for an existing theme.

Note that system themes are read-only. If you want to set the logo, first copy it then set the logo on the copy. Then publish the copy.

## list

List the existing themes:

Examples:
* `dynamicscli themes list`

## publish

Publish a theme making it the default.

Examples:
* `dynamicscli theme publish mytheme`

## copy

Copy a theme.

Flags:
* --merge-json: Merge a json formatted file of values into the copy. This allows you to change the properties of a theme, such as the title color.

Examples:
* `dynamicscli copy "CRM Default Theme" mytheme`: Copy an existing theme.
* `dynamicscli copy "CRM Default Theme" mytheme --merge-json newcolors.json`: Copy a theme but changing the values as described in newcolors.json.

```json
{
  "navbarshelfcolor": "#FAFAFA"
}
```

## set-logo

Set the logo on an existing theme. The logo is specified as a web resource. You must us the "name" of the webresource. The webresource name is often the virtual path setup by a developer, but not always. You can obtain the names of webresources using `dynamicscli webresources list` and grep through the output or use something like `--filter logo` if you already know part of the logo's webresource name.

Examples:
* `dynamicscli themes set-logo mytheme '/new_/logo.gif'`: Set the logo on mytheme to the webresource named '/new_/logo.gif'.

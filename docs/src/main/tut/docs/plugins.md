---
layout: docs
title: plugins
---

plugins allows you to upload an assembly (.dll) file to the dynamics server. It can optionally watch the plugin .dll file for changes and upload the plugin automatically. The plugin must already be registered. You can use the SDK's plugin registration tool or some other mechanism to register your plugin. In dynamics, an assembly file can hold a workflow, action or command. The are all variations of the same thing, a bit of code to executed in a sandbox when triggered in some way. An assembly must also strong named, which is a digital signature of the assembly.

## Subcommands

* [upload](#upload): Upload a plugin to a dynamics server.

### upload
The publickeytoken is set to null for the upload explicitly to allow the plugin to upload without an error and without needing to load .net assemblies in the CLI program itself.

```sh
dyanmicscli plugins upload <dll-file> [--watch]
```

* --watch: Watch the .dll for changes. When it changes, upload it. If the .dll is deleted, do not try to delete it from the server.

## Examples

Example:
```sh
dynamicscli plugins upload ~/vsprojects/CRMPluginSolution/CRMPluginProject/bin/Release/CRMPluginProject.dll
```

## Futures
* Read the publickeytoken from the .dll through reflection or sn.exe and upload the publickeytoken value.
* Read metadata/json config file to allow full registration when uploading a new assembly.

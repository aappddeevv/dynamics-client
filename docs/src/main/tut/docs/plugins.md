---
layout: docs
title: plugins
---

plugins allows you to upload an updated .ddl file to the dynamics server. It can optionally watch the plugin .dll file for changes and upload the plugin automatically. The plugin should already be registered, say, using the SDK's plugin registration tool or some other mechanism.

The publickeytoken is set to null for the upload explicitly to allow the plugin to upload without an error. 

TODO: Read the publickeytoken from the .dll through reflection or sn.exe and upload the publickeytoken value.

Example:
```sh
dynamicscli plugins upload ~/vsprojects/CRMPluginSolution/CRMPluginProject/bin/Release/CRMPluginProject.dll -c dynamicsorg.json
```

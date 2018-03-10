---
layout: home
---

# dynamics-client

**dynamics-client** is a Microsoft Dynamics CLI and browser/server library that executes complex tasks to help you create great Dynamics solutions.

# Getting Started
The CLI runs under node. Make sure that the latest node is installed. node v8.x should be fine.

Install from node:
```sh
npm i -g dynamics-client
```
Then use it
```sh
dynamicscli --help
// or if you need to increase the stack size, or --es-staging=true
node --stack-size 3000 dynamicscli --help
```
The CLI uses the newer Dynamics Web API which requires dynamicscli to have an application id. The application id can be created using the process described at [MSDN](https://msdn.microsoft.com/en-us/library/mt622431.aspx). The latest Web API uses OAuth 2.0. You will need administrator access to generate the application id. The id only needs to be generated once. If another Web API client has already been registered similarly, you can probably use that id for dynamicscli.

# Documentation
CLI documentation can be found [here](docs/index.html).

Integrated API documentation from each module can be found at [here](api/dynamics/index.html).

# Motivation
Microsoft Dynamics has many entry points including a REST interface based on the OData standard. During the course of a Dynamics implementation, you typically have need to perform many different types of operations from downloading data, uploading data or managing workflows and solutions. You can use powershell for some of these operations. Other tools are also available such as the Plugin Registration Tool, which while fairly useful interactively, is not helpful for batch/automated execution. Solution Packager is another helpful tool that is open source and uses Dyanmics' APIs.

dynamics-client contains many useful tools under one CLI that is easily scriptable. Taken together, you can automate many aspects of a Dynamics implementation. The underlying code is also highly performant so you can export and upload very large datasets easily but quickly. dynamics-client is a swiss army knife for implementing Dynamics applications.

# Current State
While the tool has been used in multiple projects, it is still needs improvements in the documentation and easy of use. Some common tasks needed for Dynamics' implementation still need to be fleshed out.

# Copyright and License
All code is available to you under the MIT license.

Copyright 2017 The Trapelo Group

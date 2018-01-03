---
layout: docs
title: Solution Deployment
---

The dynamics SDK provides a solution deployer tool which you can use to bundle together different parts of an overall solution and then "run" it to deploy the components.

It's actually much easier to create a solution deployment script with some simple scripts and if you want to bundle everything together you can just zip the contents. It also makes it much easier to put the contents under version control if binaries are not involved.

dynamics-client is developed with the intent to provide all the individual pieces that allow you to assemble *any* type of solution deployment script that meets your needs but does not force a compilation or build step on you that makes it difficult to work with the artifacts. Since most of the data needed to configure dynamics is accessible via APIs and the data model, this model of deployment is achievable.

## Common Solution Deployment Tasks


## Examples

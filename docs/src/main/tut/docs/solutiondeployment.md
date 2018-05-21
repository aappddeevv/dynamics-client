---
layout: docs
title: Solution Deployment
---

# Solution Deployment

The dynamics SDK provides a solution deployer tool which you can use to bundle
together different parts of an overall solution and then "run" it to deploy the
components.

It's actually much easier to create a solution deployment script with some
simple CLI scripts and if you want to bundle everything together you can just zip
the contents. It also makes it much easier to put the contents under version
control if binaries are not involved.

dynamics-client is developed with the intent to provide all the individual
pieces that allow you to assemble *any* type of solution deployment script that
meets your needs but does not force a compilation or build step on you that
makes it difficult to work with the artifacts. Since most of the data needed to
configure dynamics is accessible via APIs and the data model, this model of
deployment is achievable.

## Deploy Solution, Load Config Data

Here's an example that deploys a solution then loads statically prepared configuration data.

First we create a script `deploy`:

```sh
#!/bin/env sh
dynamicscli solution upload mysolution_1_0_0.zip
dynpamcscli update entity myconfigs myconfig.json --upsertpreventcreate false --pk myconfigid
```

and ensure the config data is available:

```sh
{
  "myconfigid": "baff8d69-b231-e811-a94d-000d3a36399f",
  "configvalue": "10"
}
```

Now our deployment files, in the same directory, looks like:
```sh
deploy
myconfig.json
```

That's it! We could also zip the contents to create a bundle and unzip them on a
target system that is performing the deployment.

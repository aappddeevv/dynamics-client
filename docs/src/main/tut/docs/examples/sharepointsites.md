---
layout: docs
title: Sharepoint Sites
section: examples
---

# Configuring Sharepoint Sites

Sharepoint sites in dynamics are kept as "sharepointlocation" records. To help,
as much as possible, automate deployment to a new org or when you are testing
deployments in general, you want to load the sharepoint site record as
configuration data. The dynamics team provides a "configuration data"
program. But we can do that quite easy just by upserting a record.

* Create a json record of the sharepoint site including a guid.
* Use dynamicscl to "update" (really upsert) the record into the target org.
* Don't forget to "manually" validate the site. You can't automate this through
  an action/function at this time.

Here's a sharepoint record to keep as a file. I did not create this by hand,
merely dumped the existing record from a dev site and then deleted non-essential
fields.

Dump:

```sh
dynamicscli entity export-from-query '/sharepointsites' --outputfile sharepointsites.json
```

The record with some manual edits. You must create the guid id or use the one from the dump file above, which is perfectly fine. The CLI [jq](https://stedolan.github.io/jq/) and its tutorial [tutorial](https://shapeshed.com/jq-json/) may be helpful for manipulating json data extracted from dynamics.

```json
{
    "sharepointsiteid": "baff8d69-b231-e811-a94d-000d3a36399f",
    "description": "Document storage",
    "ispowerbisite": false,
    "servicetype": 0,
    "absoluteurl": "https://yourorg.sharepoint.com/sites/DynamicsCRMYeah",
    "name": "DynamicsCRMYeah",
    "folderstructureentity": "none"
}
```

The load:

```sh
dynamicscli update entity sharepointsites sharepointsite.json --pk sharepointsiteid --upsertpreventcreate false
```

Specifically for the sharepoint site, you may need to add the "grid" component
and validate it for use prior to using it. These steps are not subject to
automation at this time.

You can do this for any type of configuration data that you wish to upload for a
new dynamics org.

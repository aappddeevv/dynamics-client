---
layout: docs
title: update
---

The `update` command allows you to update entities based on an input json file.

## Subcommands
* entity: Update records as specified on the command line.

This command is designed to process a load ready file. It does not perform
lookups or other transformations. It can add/drop/rename attributes in the json
object. Each json object in the file should be in json streaming format, json
objects separated by newlines.

Let's say you downloaded reference and key information to your local
database. You might then run a sql command that mergesq the source data with the
key/reference data.

For example, I might need to update the name attribute of my entity and I use the csv toolkit alot under linux, so:

```sh
sql2csv--db $DB update-new_myentity.sql  | csvjson --stream > new_myentity_name_update.json

```

This might produce:
```sh
{ "new_name": "some name", "new_myentityid": "f0ca7700-fb29-e811-a94f-000d3a324a3e" }
{ ... }
{ ... }
```

Then you can run:
```sh
$CLI update entity new_myentitys new_myentitys.json --pk new_myentityid
```

And the update is performed. If you have extra fields in the json that should not be part of the update for any reason, you can use the `--drops col1,col2` option to remove them.


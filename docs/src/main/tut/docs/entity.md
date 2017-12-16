---
layout: docs
title: entity
---

The `entity` command allows you to export data from a CRM server at fairly high speed. You can export in CSV or JSON format using a web api query that you specify on the command line or through CLI args that will compose the web api query string for you.

Subcommands include:
* export: Export entity data. Command line options allow you to build up a query using web api fragments. dynamicscli will stitch them together.
* export-from-query: Provide a web api query to specify the data to export. It can be trick to properly single quote value inside the web api query so watch your OS shell expansion carefully.
* count: Count the number of entities. If you provide the --repeat option, it will continuously count entities, which can be resource intensive so use --repeat wisely. You can provide a list of entities to count.
* delete-by-query: Delete data by providing a query. If you created a load id for your data loads or use the builtin load id, you can delete a slice of the data based on the id. Otherwise, the query must return the primary key which will be used to issue the delete commands.


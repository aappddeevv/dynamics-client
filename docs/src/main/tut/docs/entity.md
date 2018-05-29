---
layout: docs
title: entity
---

# entity

The `entity` command allows you to export data from a CRM server at fairly high
speed. You can export in CSV or JSON format using a web api query that you
specify on the command line or through CLI args that will compose the web api
query string for you.

## Subcommands
* export: Export entity data. Command line options allow you to build up a query
  using web api fragments. dynamicscli will stitch them together. Output is csv.
* export-from-query: Provide a web api query to specify the data to export. It
  can be trick to properly single quote value inside the web api query so watch
  your OS shell expansion carefully. The only output format is json.
* count: Count the number of entities. If you provide the --repeat option, it
  will continuously count entities, which can be resource intensive so use
  --repeat wisely. You can provide a list of entities to count. Count requests
  are run in parallel.
* delete: Delete data by providing a query. If you created a load id for your
  data loads or use the builtin load id, you can delete a slice of the data
  based on the id. Otherwise, the query must return the primary key which will
  be used to issue the delete commands. It is suggested that you first run count
  or export the entities you want to delete to ensure your delete query will
  select the correct records.

### count
You can specify any/all `--query`, `--query-file` or ``--filter` to select the entities to count.

* `--query`: key=value pairs. The key will be used to sort the count results.
* `--query-file`: A JSON file with key, value pairs.
* `--filter`: Specify only the entity. A query will be created e.g. `--filter contact` === `--query contact='/contacts?$select=contactid`.
* `--function` with `--filter`: Use the fast function approach, but if you do, you cannot use queries to count, you can only count the entire entity. The "function" implies the use of a dynamics server provided web api function that counts entities but it is limited to counting all entities. If you want a total count, use this option because it is fast and does not impact the server.

Shell expansion can make specifying the query difficult on the command
line. `--query-file` allows you to use single quotes in your query much
easier. For example to count the number of customeraddress entities for the
account entity where an account has more than 2 addresses (since 2 are always
created):

```json
{
        "accountAddressGt2": "/customeraddresses?$select=customeraddressid&$filter=addressnumber gt 2 and objecttypecode eq 'account'",
        "accountAddress": "/customeraddresses?$select=customeraddressid&$filter=objecttypecode eq 'account'"
}
```

which you can run counts using:

```sh
dynamicscli entity count --query-file addressqueries.json
...
accountAddress, 1000
accountAddressGt2, 100
...
```

I often use counts when I think there is a load issue. For example, I was
concerned that a load set the ownerid incorrectly, so I ran a count:

```sh
dynamicscli entity count --query c='/mycustomactivity?$select=activityid&$filter=_ownerid_value eq 9f077006-2fe5-44e4-b836-54a542743460'
```

You cannot use navigation properties to in the queries to count child
entities. Reverse the query and run the count on the child record.

Note that there is a function provided in the API to count objects but it does
not take 'query' arguments so its not overly useful. It is very fast though, so
if you need to count the entire set of entities, use it:

```sh
dynamicscli entity count --function true --filter contact,account
```

### export, export-from-query
Exporting data comes in two flavors. Exporting using a query created from parts
on the command line and using a fully formed query. You can export data into a
CSV or JSON streaming file.

Both of these options take:
* include-formatted-values: Include formatted values in the output.
* max-page-size: The pagesize to use when retrieving data. You should use the
  default unless you see stack or heap errors. Either increase the head/stack or
  decrease max-page-size to fix.
  
#### export
You can dump a single json object using `--raw` in order to see some example
output. Then use the rest of the parameters to compose a web api query to select
data to be exported.

Examples:
* `dynamicscli entity export contacts --outputfile contacts.csv`: Export all contacts to csv file.

#### export-from-query
* query: Specify the query here. Watch out for shell escapes if you use single
  quotes on linux. You can limit results using $top.
* wrap: Wrap the output in json array brackets and separate records with a
  comma.
* skip: While highly inefficient, this will skip the first N records. There's no
  clever way to jump to a results page efficiently.

The default is to output JSON strings separated by newlines.

Examples:
* `dynamicscli entity export-from-query '/contacts' --outputfile contacts-from-query.json`: Dump all contacts to json file.

### delete
By providing a web api, you can delete those records selected by the query. Each
query must return the primary key in the query and you must specify which entity
you are deleting so that the metadata for the entity can be looked up and the
primary key identified correctly.

* `--query`: Specify these to delete records based on a query.
* `--query-file`: Create a CSV file that has two columns title, "entity" and
  "query". Make sure you properly handle embedded commas. dynamicscli will read
  the csv file and delete the records specified in the queries in the order
  specified.

The current version does not use the OData batch to delete the records so a
concurrency factor much higher than the default should be used e.g. 100.

The number of deleted records and the respective query are printed as output.

Here's an example query file:

```text
entity,query
contact,/contacts?$select=contactid&$filter=age gt 25
account,/accounts?$select=accountid&$filter=importsequencenumber eq 20
```

You can run a bulk delete job directly on CRM and use a sophisticated FetchXml
query. This delete functionality is designed to handle simple delete scenarios
that you often run into after a bad data load in a script.

To find the sequence number of an import, use `dynamicscli importdata list-imports`.

Examples:
* `dynamicsclient entity delete connection
  '/connections?$select=connectionid&$filter=importsequencenumber eq 176`: Delete connections that
  were imported with an import sequence number of 176.
* `dynamicsclient entity delete --query-file deleteme.json`: Delete based on the queries contained in the
  json file similar to above.

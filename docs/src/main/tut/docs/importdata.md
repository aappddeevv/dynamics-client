---
layout: docs
title: importdata
---

# importdata

importdata allows you to importdata. Combined with other CLI programs such as
csv_splitter.py (included) and the program "parallel", you can easily create and
execute parallel loads using the standard data import facility. This allows you
to import a substantial amount of data quickly using the standard data import
wizard design tools on the dynamics platform. Most importantly its free and you
do not need to engage expensive ETL developers. If you are able to load data
through a spreadsheet, you can use `importdata` to perform high-speed loading.

"parallel" is a program you can install on both Windows, Linux and MacOS systems to run commands in parallel.

To import gigabytes of data quickly, break your load .csv load files into
smaller portions below the 22 MB import limit for dynamics online, then use
parallel to run dynamicscli in parallel. For example, if you have a large file
that is broken into 40 chunks, you can use parallel to load 10 chunks
simultaneously. If one chunk finishes earlier than the others, parallel starts
running another chunk.

You will need to find the limit of your system, some online systems during
certain parts of the day are more tolerant of fast data loading than other
times. A small amount of experimentaton to determine the optimal # chunks/#
simultaneous chunk combinations.

## Subcommands
* [bulkdelete](#bulkdelete): Create a bulkdelete job from a query file.
* [delete](#delete): Delete imports (jobs) by name.
* [dump-errors](#dump-errors): Dump detailed info about importfiles that import errors. In the UI, you can only export the rows, this dump gets everything.
* [import](#import): Import data. Specify a data file and an available import map. The
  import map must already be loaded.
* [list-imports](#list-imports): List imports, status and ownership information. This also lists the sequence number.
* [list-importfiles](#list-importfiles): List import files. These are typically created as part of creating an import job. You should run this command when you want to see the status of the import files e.g. what's being processed, error counts, etc.
* [resume](#resume): Resume in imported job at the last processing stage. I'm not sure how
  well this works.

## bulk-delete

NEED MORE EXPLANATION HERE

Bulk delete an import. This uses a json configuration file so it is a bit complex to setup.

Examples:

* `dynamicscli importdata bulkdelete bulkdeleteconfig.json`

## delete

delete is often used after a bad import or you just want to clear out imports that were successful. Delete the import will also delete the underlying import files. You can use also `dynamicscli entity delete ...` for a more general delete approach by specifying the "import" entity. The name is used as a match so be very careful on the name you provide.

Examples:
* `dynamicscli entity delete --filter "some import name"`: Delete the imports whose name matches "some import name".


## dump-errors

Dump all error data associated with imports assuming that an error is indicated
by a failure or partial failure count. This command currently outputs only json
so its best to browse it *after* you process it for beautification
e.g. `js-beautify -r errors.json`.

Examples:

* `dynamicscli importdata dump-errors --outputfile errors.json`


## import

You can combine this command with `parallel` (available for most major OSes) and perform highly parallel loads. Using `csv_splitter.py` to split your file into file parts with only 5-10K rows of data each seems to provide a good throughput on loads.

Examples:
* `dynamicscli entity import accounts.csv accounts-map`: Load the CSV file `accounts.csv` using the previously loaded import map `accounts-map`.

If your import does not set the "owner" for the records, they will default to the user performing the import. To change that, use `--recordsownerid <guid>`. You can obtain the owner guid from a dump using dynamicscli or by looking at the security views. To dump the systemeuser records, use `dynamicscli entity export-from-query '/systemusers?$select=lastname,firstname,systemuserid' --output-file users.json` and look for the user of interest.

Using the `parallel` program you can do:

```sh
parallel --jobs 10 --results logs/{/} $CLI importdata import {} accounts-map ::: "$DATADIR"/accounts.csv_*.csv
```

The log files for each "part" will be placed into the directory "logs" and each path under logs will represent the "input" files "$DATADIR/accounts.csv_1.csv" and so on. That way, you can see the stdout/stderr for each process that was run.

Where accounts.csv_*.csv are the file parts created by `csv_splitter.py` from the file `accounts.csv`. The command line looks complex because it uses a "generator" at the end to generate pathnames that are substituted back in the '{}' characters, but don't let it fool you. The complexity is all about creating log files for each program that is run in parallel with an unique set of arguments, that's it!.

`parallel` can  be obtained from:
* [GNU parallel](https://www.gnu.org/software/parallel): GNU version, written in perl.
* [rust parallel rewrite](https://github.com/mmstick/parallel): A GNU parallel clone written in RUST but with the same syntax.

## list-importfiles

List import files.

Examples:
* `dynamicscli importdata list-importfiles`

## list-imports

List imports. Imports will list the sequence number of the import. You can use the sequence number to delete all imported records using the dynamicsclient delete command.

Examples:
* `dynamicscli importdata list-imports`

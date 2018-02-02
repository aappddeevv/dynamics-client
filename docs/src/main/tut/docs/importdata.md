---
layout: docs
title: importdata
---
importdata allows you to importdata. Combined with other CLI programs such as csv_splitter.py (included) and the program "parallel", you can easily create and execute parallel loads using the standard data import facility. This allows you to import a substantial amount of data quickly using the standard data import wizard design tools on the dynamics platform. Most importantly its free and you do not need to engage expensive ETL developers. If you are able to load data through a spreadsheet, you can use `importdata` to perform high-speed loading.

"parallel" is a program you can install on both Windows, Linux and MacOS systems to run commands in parallel.

To import gigabytes of data quickly, break your load .csv load files into smaller portions below the 22 MB import limit for dynamics online, then use parallel to run dynamicscli in parallel. For example, if you have a large file that is broken into 40 chunks, you can use parallel to load 10 chunks simultaneously. If one chunk finishes earlier than the others, parallel starts running another chunk.

You will need to find the limit of your system, some online systems during certain parts of the day are more tolerant of fast data loading than other times. A small amount of experimentaton to determine the optimal # chunks/# simultaneous chunk combinations.

## Subcommands


## Examples

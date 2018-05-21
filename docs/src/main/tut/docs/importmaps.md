---
layout: docs
title: importmaps
---

# importmaps

importmaps imports standard Dynamics data import wizard data maps from a local file. The import maps are loaded and visible under Settings > Data Management > Import Maps.

Import maps can be downloaded from dynamics or created manually locally. Generally, the process of creating a new import maps is a bit of trial and error. You might create a spreadsheet file (.csv format) and load the data, adjusting the map multiple times prior to settling on a final data map. Then you can download the maps related to your solution. 

Once you are ready to lead data, load the import maps first. The use the CLI to load the data.

## Subcommands

* *download*: Download importmaps.
* *list*: List importmaps.
* *upload*: Upload import maps.

## download

Download all importmaps

Examples:
* `dynamicscli importmaps download`: Download all import maps.
* `dynamicscli importmaps download --noclobber`: Download all import maps but do not overwrite any. This allows you to download new importmaps that have not been downloaded.

## list

Listings can be customized by a filter:

Examples:
* `dynamicscli importmaps list`: List all importmaps.
* `dynamicscli importmaps list --filter blah`: List importmaps with the name blah in it.

## upload 
Example:

```sh
dynamicscli importmaps upload ./accountsmap.xml
```

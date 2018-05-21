---
layout: docs
title: ETL
---

# etl

You can write a simple ETL program using the dynamics-client library. Some ETL
supporting functions are available to assist you. With this, you write simple
ETL programs that perform more advanced manipulation of your data while still
keeping the final update/insert/delete step performant.

The ETL program you write can be sequenced with other CLI commands. Stitching
together these parts creates a data migration/management solution.

The library can automatically handle:

* batching
* asynchronous processing
* concurrency

The library runs on node, so there is no parallelism but it turns out that for
the class of problems dynamicscli solves, parallelism is not critical. Using
dynamicscli for a full ETL replacement is *not* a target usage scenario.


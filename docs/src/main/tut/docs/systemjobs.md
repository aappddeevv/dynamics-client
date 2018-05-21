---
layout: docs
title: systemjobs
---

# systemjobs

`systemjobs` allows you to list and delete systemjobs. You can select the jobs to delete based on their type, such as all completed jobs or all canceled jobs.

```sh
dynamicscli systemjobs delete-completed
```

You can delete different statuses using:
* delete-completed
* delete-canceled
* delete-failed
* delete-waiting
* delete-waiting-for-resources
* delete-inprogress


---
layout: docs
title: actions
---

# actions

The `actions` command issues Action requests to the Dynamics server then prints the payload response to the standard output. 

## Subcommands
* [execute](#execute): Execute an action.

### execute
Execute an action specified on the command line using an optional json formatted payload file.

* --payload-file: Optional file that holds the fully formed payload. No processing is performed on the payload file.
* --pprint: Pretty print the returned JSON value instead of printing the raw string returned in the response.

## Examples

```sh
dynamicscli action execute custom_action_name --payload-file payload.json -c crm.json
```
## Future

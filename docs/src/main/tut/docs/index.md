---
layout: docs
title: CLI
---

The default CLI program provides multiple types of functionality.

* A swiss-army knife utility that performs a multitude of tasks. You can perform
  some of these tasks using other available tools or in some case,
  powershell. The tasks are arranged in command/subcommand type format.
* A library that can be used in browser programs.
* A library that can be used in server based programs including creating your
  own "derived" CLI program or a standalone tightly focused CLI program, such as
  a dedicated ETL program or a CLI program that only allows a subset of tasks
  that the default CLI program can perform.

The CLI program can be used like any other program but it has many commands and
subcommands. Overall you run the CLI like:

```sh
dynamicscli <command> <subcommand> args optional-args -c connectionfile.json
```

The `connectionfile.json` is a file that specifies the connection parameters in
json format. By default it is named 'dynamics.json' and it looks in the current
directory for it. If the file exists, you can just type `dyanmicscli <command>
<subcommand> args optional-args`. If the enviroment variable
`DYNAMCIS_CRMCONFIG` is defined, that value will be used. The precedence is the
CLI parameter, the environment variable and then the default `./dynamics.json`.

Environment variables include:
* DYNAMICS_CRMCONFIG: connection config file
* DYNAMICS_IMPERSONATE: impersonate crm systemuserid
* DYNAMICS_PASSWORD: dynamics org password

Throughout the rest of the documentation, we will assume that DYNAMICS_CRMCONFIG is pointing to a config file and leave that agument (--c or --crm-config) off of the examples.

dynamicscli - a nodejs CLI application and client framework for working with online Microsoft Dynamics applications. The application will not currently authenticate with on-premise systems (or at least I do not think so).

dynamicscli uses the ODatav4 REST data api which means that you must register the application with Azure Active Directory to allow the CLI to connect to the server. Registering is easy to do, but you need Admin access to create the registration. You can use any application registration that allows your userid access to the data and has an application id.

dynamics-client two personalities:
* A CLI that can be used from any OS. While one could use PowerShell for everything, its much easier to add commands or make commands robust using an application directly.
* An extensive client library that is performant on the nodejs platform. You can use this as a framework for developing small Dynamics applications like ETL or platform management. For example, I added a command to help manage the dynamics platform in around 30 minutes and it worked the first time.

dynamicscli is written in scala.js which is a small language with more extensive type-safety, immutability and greater consistency. It is a great language for writing logic and effects heavy code.

Why nodejs? nodejs has an extensive array of web oriented resources and is lightweight on memory while still being fast enough. You could also use the client framework for CRM web applications directly. You can also use it for hot-reload, local development that seamlessly integrates with CRM so you get easy development of new CRM UI extensions. Running a nodejs instance on azure also means that you can deploy dynamicscli to azure and have a continuously running Dynamics platform processing platform ready to go.

dynamicscli automatically handles paging through multi-paged results from dynamics as well as automatic retry.

## Installing
Currently, you need to build the solution from github. We will publish the CLI progarm on npm shortly.

```sh
npm i -g dynamics-client
```

## Running
The script can be run, assuming it is on the path:
```sh
dynamicscli --help
```
If you need to run directly with node,
```sh
node /path/to/dynamicscli <args for dynamicscli>
```

## CRM Connection Parameters
Dynamics connection parameters should be placed in a json file. The default is crm.json. The file should look like below. Some typical values are also shown but you must find the values that match your application.
```javascript
{
    "tenant" : "<yourorgname>.onmicrosoft.com",
    "authorityHostUrl" : "https://login.windows.net",
    "username" : "<userid>@<tenant>",
    "password" : "<yourpassword>",
    "applicationId" : "<appid>",
    "dataUrl": "https://<yourorgname>.api.crm.dynamics.com/api/data/v8.2/",
    "acquireTokenResource": "https://<yourorg>.crm.dynamics.com"
}
```
Your password can also be in the environment variable `DYNAMICS_PASSWORD` as you should avoid passwords in config files that may get checked into version control.

The above shows the domain name to be `<yourorg>.onmicrosoft.com` but the domain varies, It could be `mycompany.com`. The `dataUrl` can be obtained from Developers page in the CRM application's Settings area. `applicationId` can only be obtained from our Azure Active Directory application's registration page. Sometimes, applicationId is called clientId. `authorityHostUrl` should only be changed if you know that you should change it, otherwise, leave it the same as the value above.

Some information can be inferred if left out. For example, the tenant can be inferred from username and the dataUrl can be inferred from acquireTokenResource but its best to specify them directly.

Once you have created your connection file, use it with the `-c <connectionfile>` option. If you connect to several organizations just name the connection file after the organization e.g. myorg1.json or myorg2.json then use it `-c myorg1.json`. Or, specify the config file in `DYNAMICS_CRMCONFIG` and skip providing the option on the command line.

If you are using Graph via the GraphClient, use the following parameters:

```javascript
{
 ...
 "authorityHostUrl" : "https://login.microsoftonline.com",
 "dataUrl": "https://graph.microsoft.com/v1.0",
 "acquireTokenResource": "https://graph.microsoft.com",
  ...
}
```

You could also use `https://outlook.office.com` to access the outlook API directly at `https://outlook.office.com/api/v2.0` for the data url.

## Commands
The general CLI usage is `dynamics command [subcommand] [opts]`.

Use `dynamics --help` to print out help and see the commands.

## What can you do?
Some of the things you can do with the CLI include:

* List and manage:
   * Publishers
   * Solutions
   * System jobs
   * Import maps
   * Execute actions (bound and unbound)
   * plugins (one-time or watch) update an assembly (.dll) that is already registered.
   * Option sets
   * Processes - Workflows
      * List/activate/deactivate/run (against a query)
   * SDK Messages
      * List/activate/deactivate
* Webresources
   * List and manage
   * Upload / update / download
   * Watch a local filesystem and create/update/delete on the remote server
* Settings
   * Set unsettable settings
* Update records generically
   * By exporting the data using this CLI, you can move configuration or entity data to a new organization.
* Run data imports using standard Dynamics data import capabilities.
   * Upload local files and use import maps just like the UI data import wizard but via the command line.
   * If you split your files before hand and upload in parallel, e.g. linux's parallel utilities, you can load 1M rec/hour.
* Export data
   * Export entity datasets easily and quickly. CSV or json.
* Export metadata of various kinds.
* Token
   * Export a single, new auth token.
   * Continuously export a valid, refreshed token to a specific file.

See [dynamics-client](https://aappddeevv.github.io/dynamics-client) for details.

Ingredients for packaging up parts of a solution deployment are here so you can create your own scripted solution deployer by just creating a directory and a standard script.

You can use the general `GraphClient` to connect and run processing against much of the Microsoft app universe.

## Credit

All work sponsored by The Trapelo Group.

## License

MIT license. See the LICENSE file.

Copyright 2017 The Trapelo Group LLC.

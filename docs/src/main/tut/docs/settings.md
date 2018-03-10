---
layout: docs
title: settings
---

The `settings` command sets traditionally unsettable org settings using undocumented API. It is equivalent to Settings>Administration>System Settings.

This command does not support the "previews" settings, which can more rapidly change over time.

## Subcommands
* post: Post a settings file to the org.

### post
The undocumented API is not associated with the web api and hence, the `acquireTokenResource` setting in the connection info file must be set as it represent the "org" that you are obtaining authorization to access.

The command is:

```sh
dynamicscli post [--settings-file file] -c $CRMCON
```
The default settings file name is `org-settings.xml`.

The settings files is an undocumented XML file looking like:

```xml
<organization
  ...xml fragments representing settings...
</organization>
```

No validation or processing of the settings file is performed so it must be perfect. The best way to understanding what settings are available and the format of the content is to look below or, given that the documentation below is incomplete, look at the "POST" from the settings dialag window and copy the content to a settings XML file.

## XML Fragments
This is an incomplete of settings and their XML format. Since the settings file is XML, XML value settings, or reaally any value with potentially XML conflicting characters, must be url-encoded before inserting into their respective settings tag.

### enablemicrosoftflowintegration

Examples:
* `<enablemicrosoftflowintegration>true</enablemicrosoftflowintegration>`: Enable MS flow integration.

### features

features requires an embedded XML structure.

...more info needed here...

### generatealertsfor*

This is a set of flags with a value of 0 or 1.

The list includes:
* generatealertsforerrors
* generatealertsforerrors
* generatealertsforwarnings
* generatealertsforinformation

Examples:
* `<generatealertsforerrors>0</generatealertsforerrors>`

### hashfilterkeywords

regex is something like `^[\s]*([\w]+\s?:[\s]*)+`

Examples:
* `<hashfilterkeywords>^[\s]*([\w]+\s?:[\s]*)+</hashfilterkeywords>`

### ignoreinternalemail

This is the `Track emails sent between Dynamics 365 users as two activities` option.

Examples:
* `<ignoreinternalemail>true</ignoreinternalemail>`: This checks the box that is, uses two activities for an email, in and out.

### isexternalsearchindexenabled

Enable relevance searching.

Examples:
* `<isexternalsearchindexenabled>true</isexternalsearchindexenabled>`: Enable it.

### isautosaveenabled

Turn on form auto-save.

Examples:
* `<isautosaveenabled>true</isautosaveenabled>`: Turn on autosave.

### ispresenceenabled

Turn on skype presence

Examples:
* `<ispresenceenabled>true</ispresenceenabled>`: Turn on skype.

### istextwrapenabled

true or false to wrap text. You should really usually have this set to true.

Examples:
* `<istextwrapenabled>true</istextwrapenabled>`: Enable text wrapping.

### maxappointmentdurationdays

The maximum calendar appointment duration days. This mostly helps you avoid really bad data entry for calendar appointment durations.

Examples:
* `<maxappointmentdurationdays>15</maxappointmentdurationdays>`: Maximum calendar duration is 15 days.

### maxuploadfilesize

`size` in bytes for the max upload file size associated with emails attachments and webresources.

Examples:
* `<maxuploadfilesize>15362049</maxuploadfilesize>`: 15MB file size limit

### plugintracelogsetting

Enable/disable trace log settings.

The values are:
* 0: Off
* 1: Exception
* 2: All

Examples:
* `<plugintracelogsetting>1</plugintracelogsetting>`: Enable it.

### sessiontimeout*

Max duration is 1,440 minutes.

Examples:
* `<sessiontimeoutenabled>true</sessiontimeoutenabled>`: Enabled custom timeout.
* `<sessiontimeoutinmins>1440</sessiontimeoutinmins>`: Set session time-out to 1440 minutes.
* `<sessiontimeoutreminderinmins>20</sessiontimeoutreminderinmins>`: Set reminder before expire.

### servestaticresourcesfromazurecdn

Serve content from azure CDN. You should mostly set this to true.

Examples:
* `<servestaticresourcesfromazurecdn>true</servestaticresourcesfromazurecdn>`: Use azure CDN.

### trackingprefix

Email tracking prefix. Semi-colon separated list. Usually the token has a colon.

Examples:
* `<trackingprefix>MyOrg:</trackingprefix>`: Use MyOrg: as the prefix for the tracking token.

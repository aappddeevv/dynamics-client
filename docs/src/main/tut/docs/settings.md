---
layout: docs
title: settings
---

The `settings` command sets traditionally unsettable org settings using undocumented API. It is equivalent to Settings>Administration>System Settings.

This command does not support the "previews" settings, which can more rapidly change over time.

Some more settings changers:
* A CLI MS provided tool, [OrgDbOrgSettings](https://support.microsoft.com/en-us/help/2691237/orgdborgsettings-tool-for-microsoft-dynamics-crm) that changes some "registry" type settings.
* Graphical UI settings changer: https://github.com/seanmcne/OrgDbOrgSettings
* Powershell: https://github.com/seanmcne/Microsoft.Xrm.Data.PowerShell

Generally, you should use the above tools first before this one. Use the powershell one first it is supported by some microsoft employees for over two years now.

This tool allows you to set them via static json formatted text file so once you have it setup, you are all set.

The settings allowed in the json file are documented here:
* https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/organization?view=dynamics-ce-odata-9

However, its easier to just run the `list` command, find your org, then see what the settings names and values are.

## Subcommands
* post: Post a settings file to the org.
* list: Dump out an unformatted json record of the settings. Use this to see the current settings. This dumps all settings for all orgs with json "formatted values" so the output is quite voluminous.
* categorizedsearch: Set the entity list for categorized list. NOT WORKNG YET!

## post

Assuming org-settings.json contains a 10MB upload limit:

```sh
{ "maxfilesize": 10000000 }
```

Example:

```sh
dynamicscli settings post -c $CRMCON
```

## list
List all settings for all orgs:

Example:

```sh
dynamicscli settings list -c $CRMCON
```

## categorizedsearch

Set the categorized search entity list. You must provide the entire list. A good default list is: account,contact,systemuser,activitypointer,lead,incident,opportunity,competitor,appointment.

Examples:
* dynamicscli categorizedsearch account,contact,systemuser,activitypointer,lead,incident,opportunity,competitor,appointment  -c $CRMCON

---
layout: docs
title: Manage themes
section: examples
---

# Managing themes

Themes are small configuration records that hold colors and a link to a
webresource that acts as the logo. It is typical that you want to change the
logo for an instance. The logo change requires you to manually clone a theme,
identify the webresource, then publish it. Once published, the "clone" becomes
the current "default" theme used by all applications.

We can dump the theme data:

```sh
$dynamicscli entity export-from-query "/themes" --outputfile themes.json
```

This produces a file with "streaming json" records.

```json
{"@odata.etag":"W/\"33016994\"","defaultentitycolor":"#666666","statecode":1,"defaultcustomentitycolor":"#00CCA3","statuscode":2,"logotooltip":"Microsoft Dynamics 365","controlborder":"#BDC3C7","controlshade":"#FFFFFF","selectedlinkeffect":"#F8FAFC","globallinkcolor":"#1160B7","processcontrolcolor":"#358717","name":"CRM Blue Theme","headercolor":"#0078D7","versionnumber":33016994,"createdon":"2018-03-13T11:13:22Z","panelheaderbackgroundcolor":"#F3F3F3","hoverlinkeffect":"#E7EFF7","navbarshelfcolor":"#FFFFFF","backgroundcolor":"#FFFFFF","_modifiedby_value":"89fd39c6-1c2c-45ac-be07-e3d304191866","modifiedon":"2018-04-22T06:36:36Z","_organizationid_value":"8758de86-3bbb-4f6f-b17a-42182bb1bca2","accentcolor":"#E83D0F","isdefaulttheme":false,"themeid":"581ba8b5-f3cf-422e-8030-e2a645b39971","_createdonbehalfby_value":"89fd39c6-1c2c-45ac-be07-e3d304191866","_createdby_value":"89fd39c6-1c2c-45ac-be07-e3d304191866","pageheaderbackgroundcolor":"#E0E0E0","maincolor":"#3B79B7","type":false,"navbarbackgroundcolor":"#0078D7","_logoid_value":null,"exchangerate":null,"importsequencenumber":null,"overriddencreatedon":null,"_transactioncurrencyid_value":null,"utcconversiontimezonecode":null,"timezoneruleversionnumber":null,"_modifiedonbehalfby_value":null}
...more json theme records...
```

The default theme has `isdefaulttheme` true. The above theme is *not* the
default.

We can now just modify the theme and reload it with our changes. We could use a
CLI to modify the JSON but its easier and more likely, that we want to create a
repeatable load file. In the case below, we change the tooltip the name and the
logo. The id for the logo came from running `dynamicscli webresources list` and
looking for our logo id in the listing output.

```sh
{ 
    "themeid": "581ba8b5-f3cf-422e-8030-e2a645b39971",
    "logotooltip": "My Logo!",
    "name": "My Company",
    "logoimage@odata.bind": "/webresources(e9238572-4542-e711-80ff-c4346bac093c)"
}
```

Then update your theme via the CLI:

```sh
dynamicscli update entity themes --pk themeupdate.json
```

This is useful if you are updating the same instance at different times, but the
reality is that when you want to modify a theme, you are usually doing that on a
new org. In this case, you need to clone an existing theme and update it or you
can create a "canned" theme record like the above ensuring that you set all the
values and load that using "update" but setting "--upsertpreventcreate false" to
allow a new record to be created when the themeid is not found. The new record
would have the themeid as specified in the update record.

Fortunately, there is some theme support in dynamicscli. Suppose we want to
change a color and set the icon:
```sh
// changes.json
{ "navbarshelfcolor": "#FAFAFA" }
```

Then we can do:

```sh
// copy the theme
dynamicscli themes copy "CRM Default Theme" mytheme --merge-json changes.json

// set the logo icon, where the "name" of the logo reflects its virtual path
// a common pattern for naming web resources.
dynamicscli themes set-logo mytheme "new_/images/logo.gif"

// publish it
dynamicscli themes publish mytheme
```

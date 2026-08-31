# Unstructured Query Language (uql)
***
Over the past 7 or so months, I've been working on and off to build my own clone of SQL, and here it is.
***
table of contents:
- [syntax](#syntax)
- [api](#api)
- [how it works](#how-it-works)
***
## Syntax
the syntax is very reminiscent of modern SQL.

Loading a database: load db_file

Insertion: insert into table_name value1 value2...

Table creation: create-table table_name column:type column:type column:type

Selection: select {n/all} rows from table name {where value operator value}

Updating: update table_name set column=value where value operator value

deleting: delete from table_name where value operator value

You can also make .uql files containing entire scripts, but they **MUST** start by loading a database file.
***
## API

Similar to postgres, I added a tool which allows users to post to xxx.xxx.x.xx:8080 with the query externally, using the Java Spark system. 

It is enabled by running the .jar file with the argument 'api'

the post's json must look like this:

```json
{

    "requests": ["load db_file", "request", "request"...]

}
```

I forgot how the response json is supposed to look so good luck!

***
## How it Works

faith and hope is all that is holding this project together :)
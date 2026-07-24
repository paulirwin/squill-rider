# squill-rider

A JetBrains Rider plugin for [Squill](https://github.com/paulirwin/squill) support.

Squill brings SSDT-style declarative, code-first database projects and DACPAC deployments to
PostgreSQL and MariaDB/MySQL. This plugin adds IDE support for editing, building, and deploying
Squill `.squillproj` projects in Rider.

## Features

### Project recognition

`.squillproj` projects get a database icon in Solution Explorer, matching how Rider presents
`.sqlproj`. Because `.squillproj` is a standard MSBuild SDK project, Rider already loads, builds,
and cleans it natively, so the plugin doesn't need to add build actions.

### Provider-aware SQL dialect

The provider declared in `<SquillProviderName>` is applied as the SQL dialect for the project's
`.sql` files, so highlighting and inspections match the database you're actually targeting:

| `<SquillProviderName>` | Dialect |
| --- | --- |
| `Postgresql` / `Postgres` | PostgreSQL |
| `MariaDb` | MariaDB |
| `MySql` | MySQL |

Values are matched case-insensitively. A project with no provider set falls back to PostgreSQL,
matching the SDK's own default.

### Cross-file object resolution

References between `.sql` files resolve without a live database connection, whether that's a view
selecting from a table in another folder or a foreign key pointing at another schema. The plugin builds an
offline DDL data source per project, so navigation and completion work against the schema as
written in source.

### Deploy and Script run configurations

Two run configuration types wrap the Squill CLI's verbs:

- **Script**: generate the deployment script without applying it. Only reads the target's
  schema, so it's the safe way to preview a change.
- **Deploy**: diff the project against a target database and apply the changes.

Both build the project first, then invoke the CLI against the resulting DACPAC. Options map
directly onto the CLI's flags:

| Option | Effect |
| --- | --- |
| Dry run | Print the SQL that would run without applying it (Deploy only) |
| Disallow table rebuilds | Fail rather than rebuild a table to apply a change |
| Drop objects not in source | Drop objects present in the target but absent from the project |
| Allow data loss | Permit changes that may drop data (Deploy only) |

The defaults are the CLI's own: table rebuilds are allowed, dropping is off, and deployment is
blocked on possible data loss.

#### Connections

The target database is chosen from the connections you've already configured in Rider's Database
tool window, rather than by typing a connection string into the run configuration.

Only the data source's *identity* is stored in the run configuration. The URL is read back at
launch and the password comes from the IDE's credential store, so no secret is written to the run
configuration XML, which is routinely committed to version control. The password is also masked
in the console command echo.

One caveat: the connection string is passed to the CLI as a process argument, so it is visible in
the process table while the deploy runs. That is inherent to invoking the CLI and can't be avoided
without a change to Squill itself.

If a data source stores credentials in its JDBC URL query string rather than in the separate
username and password fields, those are picked up as a fallback.

## Requirements

- Rider 2026.1 or later.
- The [Squill CLI](https://github.com/paulirwin/squill), for the Deploy and Script run
  configurations only. Everything else works without it.

  ```bash
  dotnet tool install --global Squill
  ```

  The plugin looks for the CLI in a local tool manifest first, then on the `PATH`, and can be
  pointed at an explicit path. If it isn't found, the run configuration says so before launching
  rather than failing partway through.

## Building

Requires a JDK 21 toolchain. Use the Gradle wrapper:

```bash
./gradlew build      # compile, assemble, and verify the plugin
./gradlew unitTest   # fast, pure-logic tests (no Rider download)
./gradlew test       # tests that need the IntelliJ Platform
./gradlew runIde     # launch a sandbox Rider with the plugin (first run downloads Rider)
```

Tests are split across two source sets. `src/unitTest` holds pure logic such as CLI argument
construction, connection-string translation, and provider parsing. It runs against the compiled
main output and JUnit alone, so CI can run it without downloading the multi-gigabyte Rider
archive. Platform-dependent tests live in `src/test`.

## Status

Issues for this plugin are tracked in the
[Squill repository](https://github.com/paulirwin/squill), under
[issue #57](https://github.com/paulirwin/squill/issues/57) for IDE support and
[issue #91](https://github.com/paulirwin/squill/issues/91) for publish/deploy.

Not yet implemented:

- A settings screen for the CLI path. The path is honored if set, but there is currently no UI to
  edit it.
- Solution Explorer context-menu actions. Run configurations must be created manually.
- Structured error reporting. The CLI reports every failure as exit code 1 with a prose message,
  so the plugin can't yet distinguish a data-loss block from a connection error or offer a
  targeted retry.

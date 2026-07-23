# squill-rider

A JetBrains Rider plugin for [Squill](https://github.com/paulirwin/squill) support.

Squill brings SSDT-style declarative, code-first database projects and DACPAC deployments to
PostgreSQL and MariaDB/MySQL. This plugin adds IDE support for editing and building Squill
`.squillproj` projects in Rider.

## Building

Requires a JDK 21 toolchain. Use the Gradle wrapper:

```bash
./gradlew build      # compile, assemble, and verify the plugin
./gradlew test       # run tests
./gradlew runIde     # launch a sandbox Rider with the plugin (first run downloads Rider)
```

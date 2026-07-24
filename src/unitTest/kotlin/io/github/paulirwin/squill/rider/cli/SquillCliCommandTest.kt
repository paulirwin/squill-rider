package io.github.paulirwin.squill.rider.cli

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the argv Squill's CLI actually accepts (`Squill/Program.cs`). The option spellings and
 * their default-inverted semantics are asserted literally: `--disallow-table-rebuild` and
 * `--allow-data-loss` both *opt out* of a safe default, so a builder that emitted them
 * unconditionally would silently make deploys destructive.
 */
class SquillCliCommandTest {
    private val dacpac = "/proj/bin/Debug/MyDb.dacpac"
    private val conn = "Host=localhost;Username=postgres"

    @Test
    fun `deploy emits the verb, dacpac positional, and required connection string`() {
        val args = SquillCliCommand.deploy(
            dacpacPath = dacpac,
            connectionString = conn,
            options = SquillDeployOptions(),
        )

        assertEquals(listOf("deploy", dacpac, "--connection-string", conn), args)
    }

    @Test
    fun `script emits the verb, dacpac positional, and required connection string`() {
        val args = SquillCliCommand.script(
            dacpacPath = dacpac,
            connectionString = conn,
            options = SquillScriptOptions(),
        )

        assertEquals(listOf("script", dacpac, "--connection-string", conn), args)
    }

    @Test
    fun `target database is emitted only when set`() {
        val withDb = SquillCliCommand.deploy(
            dacpac, conn, SquillDeployOptions(targetDatabase = "analytics"),
        )
        assertEquals(listOf("deploy", dacpac, "--connection-string", conn, "--target-database", "analytics"), withDb)

        val blankDb = SquillCliCommand.deploy(
            dacpac, conn, SquillDeployOptions(targetDatabase = "   "),
        )
        assertEquals(listOf("deploy", dacpac, "--connection-string", conn), blankDb)
    }

    @Test
    fun `safe defaults emit no destructive flags`() {
        val args = SquillCliCommand.deploy(dacpac, conn, SquillDeployOptions())

        // Table rebuilds are allowed and data loss is blocked by default in the CLI; a builder
        // that emitted these opt-out flags by default would change deploy semantics.
        assert(!args.contains("--disallow-table-rebuild"))
        assert(!args.contains("--allow-data-loss"))
        assert(!args.contains("--drop-objects-not-in-source"))
        assert(!args.contains("--dry-run"))
    }

    @Test
    fun `deploy emits every opt-in flag when requested`() {
        val args = SquillCliCommand.deploy(
            dacpac,
            conn,
            SquillDeployOptions(
                dryRun = true,
                allowDataLoss = true,
                disallowTableRebuild = true,
                dropObjectsNotInSource = true,
            ),
        )

        assertEquals(
            listOf(
                "deploy", dacpac, "--connection-string", conn,
                "--dry-run",
                "--disallow-table-rebuild",
                "--drop-objects-not-in-source",
                "--allow-data-loss",
            ),
            args,
        )
    }

    @Test
    fun `script emits shared flags but has no dry-run or data-loss options`() {
        val args = SquillCliCommand.script(
            dacpac,
            conn,
            SquillScriptOptions(
                disallowTableRebuild = true,
                dropObjectsNotInSource = true,
            ),
        )

        assertEquals(
            listOf(
                "script", dacpac, "--connection-string", conn,
                "--disallow-table-rebuild",
                "--drop-objects-not-in-source",
            ),
            args,
        )
    }

    @Test
    fun `script writes to a file only when an output path is set`() {
        val args = SquillCliCommand.script(
            dacpac, conn, SquillScriptOptions(outputPath = "/tmp/out.sql"),
        )

        assertEquals(
            listOf("script", dacpac, "--connection-string", conn, "--output", "/tmp/out.sql"),
            args,
        )
    }

    @Test
    fun `arguments are passed verbatim without quoting`() {
        // GeneralCommandLine quotes/escapes per-platform itself; pre-quoting here would result in
        // literal quotes reaching the CLI. A connection string with spaces must survive intact.
        val spacey = "Host=localhost;Password=a b c;Database=my db"
        val args = SquillCliCommand.deploy(dacpac, spacey, SquillDeployOptions())

        assertEquals(spacey, args[args.indexOf("--connection-string") + 1])
    }
}

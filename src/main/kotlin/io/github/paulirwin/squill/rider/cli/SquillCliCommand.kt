package io.github.paulirwin.squill.rider.cli

/**
 * Options for `squill deploy`.
 *
 * The defaults here mirror the CLI's own defaults, so a default-constructed instance produces the
 * safest possible deploy. Note that two of Squill's flags are *opt-outs* of a safe default
 * ([disallowTableRebuild], [allowDataLoss]) — see [SquillCliCommand] for why that matters.
 */
data class SquillDeployOptions(
    /** Name of the database to diff against; defaults to the connection string's `Database`. */
    val targetDatabase: String? = null,
    /** Print the SQL that would run instead of executing it. */
    val dryRun: Boolean = false,
    /** Fail rather than rebuild a table when a change can't be applied with an in-place ALTER. */
    val disallowTableRebuild: Boolean = false,
    /** Drop standalone objects present in the target but absent from the DACPAC. */
    val dropObjectsNotInSource: Boolean = false,
    /** Permit changes that may lose data. The CLI blocks on possible data loss by default. */
    val allowDataLoss: Boolean = false,
)

/**
 * Options for `squill script`.
 *
 * Deliberately not a subset of [SquillDeployOptions] as a shared supertype: `script` accepts
 * neither `--dry-run` (it never executes) nor `--allow-data-loss` (it hardcodes
 * `BlockOnPossibleDataLoss = false`), and modelling those as ignored fields would let a caller
 * set an option that silently does nothing.
 */
data class SquillScriptOptions(
    /** Name of the database to diff against; defaults to the connection string's `Database`. */
    val targetDatabase: String? = null,
    /** Where to write the script. When null the CLI writes it to stdout. */
    val outputPath: String? = null,
    /** Fail rather than rebuild a table when a change can't be applied with an in-place ALTER. */
    val disallowTableRebuild: Boolean = false,
    /** Drop standalone objects present in the target but absent from the DACPAC. */
    val dropObjectsNotInSource: Boolean = false,
)

/**
 * Builds the argument vector for a Squill CLI invocation.
 *
 * Kept free of IntelliJ Platform types so it can be unit-tested without the platform on the
 * classpath (`src/unitTest`); the caller turns the returned list into a `GeneralCommandLine`.
 *
 * Arguments are returned **unquoted and unescaped**. Quoting is the process-launcher's job, and
 * pre-quoting here would send literal quote characters to the CLI — which matters most for
 * connection strings, whose passwords routinely contain spaces and semicolons.
 *
 * Option spellings track `Squill/Program.cs`. Both verbs share the `<dacpac>` positional and the
 * required `--connection-string`; the rest are emitted only when they differ from the CLI's
 * default, so an unset option is indistinguishable from one that was never passed.
 */
object SquillCliCommand {
    const val DEPLOY_VERB = "deploy"
    const val SCRIPT_VERB = "script"

    fun deploy(
        dacpacPath: String,
        connectionString: String,
        options: SquillDeployOptions,
    ): List<String> = buildList {
        add(DEPLOY_VERB)
        add(dacpacPath)
        add("--connection-string")
        add(connectionString)
        addTargetDatabase(options.targetDatabase)

        // Emitted before the shared flags to match the order the CLI declares them in, purely so
        // the rendered command line reads the way the CLI's own help does.
        if (options.dryRun) add("--dry-run")
        addSharedDiffFlags(options.disallowTableRebuild, options.dropObjectsNotInSource)
        if (options.allowDataLoss) add("--allow-data-loss")
    }

    fun script(
        dacpacPath: String,
        connectionString: String,
        options: SquillScriptOptions,
    ): List<String> = buildList {
        add(SCRIPT_VERB)
        add(dacpacPath)
        add("--connection-string")
        add(connectionString)
        addTargetDatabase(options.targetDatabase)

        options.outputPath?.takeIf { it.isNotBlank() }?.let {
            add("--output")
            add(it)
        }

        addSharedDiffFlags(options.disallowTableRebuild, options.dropObjectsNotInSource)
    }

    /**
     * A blank target database is treated as unset: the CLI would otherwise try to resolve an
     * empty database name rather than falling back to the connection string's own `Database`.
     */
    private fun MutableList<String>.addTargetDatabase(targetDatabase: String?) {
        targetDatabase?.takeIf { it.isNotBlank() }?.let {
            add("--target-database")
            add(it)
        }
    }

    private fun MutableList<String>.addSharedDiffFlags(
        disallowTableRebuild: Boolean,
        dropObjectsNotInSource: Boolean,
    ) {
        if (disallowTableRebuild) add("--disallow-table-rebuild")
        if (dropObjectsNotInSource) add("--drop-objects-not-in-source")
    }
}

package io.github.paulirwin.squill.rider.run

import com.intellij.execution.configurations.RunConfigurationOptions

/**
 * Persisted settings for a Squill run configuration.
 *
 * Uses the platform's [RunConfigurationOptions] bean pattern, so serialization to the run
 * configuration's XML is automatic — no manual `readExternal`/`writeExternal`.
 *
 * **The connection string is deliberately absent.** Run configuration XML is routinely shared
 * through version control, and a Squill connection string carries database credentials. Only the
 * *identity* of a DataGrip data source is stored ([dataSourceId]); the URL and password are read
 * back from the platform's own credential store at launch. See `SquillConnectionResolver`.
 */
class SquillRunConfigurationOptions : RunConfigurationOptions() {
    /** Absolute path to the `.squillproj` this configuration deploys. */
    var projectFilePath: String? by string()

    /**
     * The id of the DataGrip data source to deploy against, as returned by
     * `LocalDataSource.getUniqueId()`. Stored rather than the URL so credentials stay in the
     * platform credential store.
     */
    var dataSourceId: String? by string()

    /** Optional `--target-database` override; blank means "use the connection's own database". */
    var targetDatabase: String? by string()

    /** MSBuild configuration to build before deploying. */
    var buildConfiguration: String? by string("Debug")

    // --- CLI flags -------------------------------------------------------------------------
    //
    // Defaults mirror the CLI's own defaults so a newly created configuration is the safe one.
    // Both `disallowTableRebuild` and `allowDataLoss` are opt-*outs* in the CLI; see
    // SquillCliCommand.

    var dryRun: Boolean by property(false)
    var disallowTableRebuild: Boolean by property(false)
    var dropObjectsNotInSource: Boolean by property(false)
    var allowDataLoss: Boolean by property(false)
}

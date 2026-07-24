package io.github.paulirwin.squill.rider.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.paulirwin.squill.rider.SquillBundle
import io.github.paulirwin.squill.rider.cli.SquillCliResolver
import java.io.File

/**
 * Which Squill verb a configuration runs.
 *
 * `script` is read-only — it only reads the target's schema — while `deploy` mutates it. They are
 * separate factories rather than a flag so the distinction is visible in the run configuration
 * list, where picking the wrong one has very different consequences.
 */
enum class SquillVerb(val verb: String, val displayName: String) {
    DEPLOY("deploy", "Deploy"),
    SCRIPT("script", "Script"),
}

/**
 * A run configuration that builds a `.squillproj` and then invokes the Squill CLI against a
 * chosen DataGrip data source.
 */
class SquillRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    val squillVerb: SquillVerb,
) : RunConfigurationBase<SquillRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): SquillRunConfigurationOptions =
        super.getOptions() as SquillRunConfigurationOptions

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        SquillRunConfigurationOptions::class.java

    override fun getConfigurationEditor(): SettingsEditor<out RunConfigurationBase<*>> =
        SquillRunConfigurationEditor(project)

    /**
     * Validated before launch so problems surface in the run configuration dialog rather than as
     * a CLI failure. The CLI collapses every error into exit code 1 with prose on stderr, so
     * anything catchable here is worth catching here.
     */
    override fun checkConfiguration() {
        val options = options

        val projectPath = options.projectFilePath?.takeIf { it.isNotBlank() }
            ?: throw RuntimeConfigurationError(SquillBundle.message("run.error.noProject"))

        val projectFile = File(projectPath)
        if (!projectFile.isFile) {
            throw RuntimeConfigurationError(SquillBundle.message("run.error.projectMissing", projectPath))
        }

        val dataSourceId = options.dataSourceId?.takeIf { it.isNotBlank() }
            ?: throw RuntimeConfigurationError(SquillBundle.message("run.error.noDataSource"))

        if (SquillConnectionResolver.findDataSource(project, dataSourceId) == null) {
            throw RuntimeConfigurationError(SquillBundle.message("run.error.dataSourceMissing"))
        }

        if (SquillConnectionResolver.buildConnectionString(project, dataSourceId) == null) {
            throw RuntimeConfigurationError(SquillBundle.message("run.error.unsupportedDataSource"))
        }

        // Surface a missing CLI here too — it is the most common first-run failure, since Squill
        // ships no tool manifest and must be installed separately.
        val cli = SquillCliResolver.resolve(
            startDirectory = projectFile.parentFile,
            explicitPath = SquillSettings.getInstance().cliPath,
            pathLookup = SquillCliResolver::lookupOnPath,
        )
        if (cli == null) {
            throw RuntimeConfigurationError(SquillBundle.message("run.error.cliNotFound"))
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        SquillRunProfileState(this, environment)
}

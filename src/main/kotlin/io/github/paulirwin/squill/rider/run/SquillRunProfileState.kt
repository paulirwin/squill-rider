package io.github.paulirwin.squill.rider.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import io.github.paulirwin.squill.rider.SquillBundle
import io.github.paulirwin.squill.rider.cli.SquillCliCommand
import io.github.paulirwin.squill.rider.cli.SquillCliResolver
import io.github.paulirwin.squill.rider.cli.SquillDeployOptions
import io.github.paulirwin.squill.rider.cli.SquillScriptOptions
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Runs the Squill CLI for a [SquillRunConfiguration].
 *
 * The `.squillproj` is built first via `dotnet build`, because the CLI takes a `.dacpac` and
 * never builds one itself. Rather than chaining two process handlers, the build is run to
 * completion synchronously and only then is the CLI launched — the CLI's output is what the user
 * cares about, and interleaving MSBuild's output into the same console obscures it.
 */
class SquillRunProfileState(
    private val configuration: SquillRunConfiguration,
    environment: ExecutionEnvironment,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val options = configuration.options
        val project = configuration.project

        val projectPath = options.projectFilePath
            ?: throw ExecutionException(SquillBundle.message("run.error.noProject"))
        val projectFile = File(projectPath)
        val buildConfiguration = options.buildConfiguration ?: "Debug"

        val connectionString = SquillConnectionResolver.buildConnectionString(project, options.dataSourceId)
            ?: throw ExecutionException(SquillBundle.message("run.error.unsupportedDataSource"))

        val cli = SquillCliResolver.resolve(
            startDirectory = projectFile.parentFile,
            explicitPath = SquillSettings.getInstance().cliPath,
            pathLookup = SquillCliResolver::lookupOnPath,
        ) ?: throw ExecutionException(SquillBundle.message("run.error.cliNotFound"))

        buildProject(projectFile, buildConfiguration)

        val dacpac = SquillDacpacLocator.expectedDacpacPath(projectFile, buildConfiguration)
        if (!dacpac.isFile) {
            throw ExecutionException(SquillBundle.message("run.error.dacpacMissing", dacpac.path))
        }

        val verbArguments = when (configuration.squillVerb) {
            SquillVerb.DEPLOY -> SquillCliCommand.deploy(
                dacpacPath = dacpac.path,
                connectionString = connectionString,
                options = SquillDeployOptions(
                    targetDatabase = options.targetDatabase,
                    dryRun = options.dryRun,
                    disallowTableRebuild = options.disallowTableRebuild,
                    dropObjectsNotInSource = options.dropObjectsNotInSource,
                    allowDataLoss = options.allowDataLoss,
                ),
            )

            SquillVerb.SCRIPT -> SquillCliCommand.script(
                dacpacPath = dacpac.path,
                connectionString = connectionString,
                options = SquillScriptOptions(
                    targetDatabase = options.targetDatabase,
                    disallowTableRebuild = options.disallowTableRebuild,
                    dropObjectsNotInSource = options.dropObjectsNotInSource,
                ),
            )
        }

        val commandLine = GeneralCommandLine()
            .withExePath(cli.executable)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(cli.workingDirectory ?: projectFile.parentFile)
        commandLine.addParameters(cli.argumentPrefix)
        commandLine.addParameters(verbArguments)

        // Launch the real command, but hand the handler a masked rendering for display. Rider
        // echoes that string into the console, where it is routinely copied into bug reports —
        // the password must not survive into it. The process itself still receives the argument
        // vector verbatim; no quoting is added, because GeneralCommandLine passes arguments
        // straight to the OS with no shell to re-split them.
        val handler = KillableColoredProcessHandler(
            commandLine.createProcess(),
            SquillCommandLineMasker.maskedCommandLine(commandLine),
            StandardCharsets.UTF_8,
        )
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    /**
     * Builds the project with `dotnet build`, throwing if it fails.
     *
     * Run synchronously and its output discarded: a build failure is reported through the
     * exception, and Rider's own build output is the better place to diagnose one. The intent
     * here is only to guarantee a fresh dacpac before the CLI reads it.
     */
    private fun buildProject(projectFile: File, buildConfiguration: String) {
        val build = GeneralCommandLine("dotnet", "build", projectFile.path, "-c", buildConfiguration)
            .withWorkDirectory(projectFile.parentFile)
            .withCharset(StandardCharsets.UTF_8)

        val process = runCatching { build.createProcess() }
            .getOrElse { throw ExecutionException(SquillBundle.message("run.error.buildFailed"), it) }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw ExecutionException(SquillBundle.message("run.error.buildFailedExit", exitCode))
        }
    }
}

package io.github.paulirwin.squill.rider.cli

import java.io.File

/**
 * What the plugin can offer a user whose Squill CLI is unusable.
 *
 * Split into remedies the plugin can *run* and ones it can only *describe*, because offering a
 * button for a command that is known to fail is worse than saying what to type: the failure
 * surfaces inside the IDE, where it looks like a plugin bug.
 */
sealed interface SquillCliRemedy {
    /**
     * A command the plugin can run on the user's behalf, behind a button.
     *
     * [note] carries guidance that doesn't fit the command itself — for example, that a local
     * tool manifest exists nearby and could be used instead.
     */
    data class RunnableInstall(
        val executable: String,
        val arguments: List<String>,
        val workingDirectory: File?,
        val note: String? = null,
    ) : SquillCliRemedy

    /** Something only the user can fix; [message] explains how. */
    data class Manual(val message: String) : SquillCliRemedy

    companion object {
        /** The NuGet package id, which differs in case from the `squill` command it installs. */
        private const val PACKAGE_ID = "Squill"

        /**
         * Maps a [SquillCliProblem] to the remedy the UI should present.
         *
         * ### Why a local install is not offered as a button
         *
         * Squill's README documents only `dotnet tool install --global Squill`, and that path
         * works. Installing into a local tool manifest reproducibly fails with:
         *
         * ```
         * Unhandled exception: Settings file 'DotnetToolSettings.xml' was not found in the package.
         * ```
         *
         * verified against Squill 0.2.0 on .NET SDK 10.0.201 with a freshly created, isolated
         * manifest. The package itself is well-formed — it declares `packageType=DotnetTool` and
         * carries `tools/net10.0/any/DotnetToolSettings.xml`, the same layout as tools that do
         * install correctly (e.g. `dotnet-ef`) — and the cached copy under `~/.nuget/packages`
         * is intact. That points at SDK-side local-tool resolution rather than a packaging
         * defect, but the cause is not established. Until it is, the local command is surfaced
         * as text so the user runs it in their own terminal and sees the real error.
         *
         * `dotnet tool restore` is a different code path, is unaffected, and is offered as a
         * button.
         */
        fun forProblem(problem: SquillCliProblem): SquillCliRemedy = when (problem) {
            is SquillCliProblem.NotInstalled -> RunnableInstall(
                executable = "dotnet",
                arguments = listOf("tool", "install", "--global", PACKAGE_ID),
                // A global install is not directory-sensitive; binding it to a working directory
                // would only couple the command to whichever project triggered it.
                workingDirectory = null,
                note = problem.manifestDirectory?.let {
                    "This solution has a tool manifest at $it. To pin the version in source " +
                        "control instead, run `dotnet tool install $PACKAGE_ID` there."
                },
            )

            is SquillCliProblem.ManifestNotRestored -> RunnableInstall(
                executable = "dotnet",
                arguments = listOf("tool", "restore"),
                // Restore locates the manifest by walking up from the working directory, so it
                // must run in the manifest's own directory to resolve the intended tool.
                workingDirectory = problem.manifestDirectory,
            )

            is SquillCliProblem.ExplicitPathMissing -> Manual(
                "The configured Squill CLI path does not exist: ${problem.path}. Update it in " +
                    "Settings, or clear it to fall back to a tool manifest or the global tool.",
            )
        }
    }
}

package io.github.paulirwin.squill.rider.cli

import java.io.File

/**
 * Where the Squill CLI was found, and how to invoke it.
 *
 * The three cases differ in more than their path: a tool installed from a local manifest is run
 * as `dotnet squill ...` from the manifest's directory, while a global or explicitly-configured
 * tool is an executable invoked directly. Callers build a command line from [executable],
 * [argumentPrefix], and [workingDirectory] rather than branching on the subtype.
 */
sealed interface SquillCliLocation {
    /** The program to launch. */
    val executable: String

    /** Arguments that must precede the verb (empty unless the CLI is invoked through `dotnet`). */
    val argumentPrefix: List<String>

    /**
     * The directory to launch from, when it matters. Only a local tool is directory-sensitive —
     * `dotnet` locates the manifest by walking up from the working directory, so launching from
     * elsewhere would resolve a different tool, or none.
     */
    val workingDirectory: File?

    /** A path configured by the user in settings; used as-is, without validation. */
    data class Explicit(val path: String) : SquillCliLocation {
        override val executable get() = path
        override val argumentPrefix get() = emptyList<String>()
        override val workingDirectory: File? get() = null
    }

    /** A tool restored from a `.config/dotnet-tools.json` manifest rooted at [manifestDirectory]. */
    data class LocalTool(val manifestDirectory: File) : SquillCliLocation {
        override val executable get() = "dotnet"
        override val argumentPrefix get() = listOf(SquillCliResolver.TOOL_COMMAND_NAME)
        override val workingDirectory get() = manifestDirectory
    }

    /** A global tool found on the PATH. */
    data class OnPath(val executablePath: File) : SquillCliLocation {
        override val executable: String get() = executablePath.path
        override val argumentPrefix get() = emptyList<String>()
        override val workingDirectory: File? get() = null
    }
}

/**
 * Why the Squill CLI could not be used, paired with the remedy the UI should offer.
 *
 * Squill documents only `dotnet tool install --global Squill` and ships no tool manifest, so a
 * user who has never installed it is the common case rather than an error state. Each case is
 * distinct because each has a different fix — the UI should never render a bare "not found".
 */
sealed interface SquillCliProblem {
    /**
     * No CLI anywhere. [manifestDirectory] is non-null when a manifest exists nearby but doesn't
     * list Squill; the offered remedy stays a global install either way (see
     * [SquillCliRemedy.forProblem]), with the manifest surfaced as guidance.
     */
    data class NotInstalled(val manifestDirectory: File?) : SquillCliProblem

    /** A manifest lists Squill but `dotnet tool restore` has not been run in [manifestDirectory]. */
    data class ManifestNotRestored(val manifestDirectory: File) : SquillCliProblem

    /** The path configured in settings does not exist. */
    data class ExplicitPathMissing(val path: String) : SquillCliProblem
}

/**
 * Locates the Squill CLI.
 *
 * Resolution order is explicit override, then local tool manifest, then PATH. The manifest
 * deliberately outranks the PATH copy: a solution that pins a tool version in source control
 * expects that version to be used, not whichever build happens to be installed globally.
 *
 * Pure and platform-free so it is unit-testable (`src/unitTest`); the filesystem and PATH are
 * reached only through injectable parameters.
 */
object SquillCliResolver {
    /** The command `Squill.csproj` exposes via `ToolCommandName`. */
    const val TOOL_COMMAND_NAME = "squill"

    private const val MANIFEST_RELATIVE_PATH = ".config/dotnet-tools.json"

    /**
     * Matches the tool entry inside a `dotnet-tools.json` `"tools"` object.
     *
     * A regex rather than a JSON parse: this runs on the no-platform unit-test classpath, which
     * has no JSON library, and the shape being matched is a fixed key emitted by the .NET SDK. A
     * malformed manifest simply fails to match, which [resolve] treats as "no local tool".
     */
    private val TOOLS_BLOCK_REGEX = Regex(""""tools"\s*:\s*\{(.*)}""", RegexOption.DOT_MATCHES_ALL)
    private val TOOL_KEY_REGEX = Regex(""""([^"]+)"\s*:\s*\{""")

    /**
     * Resolves the CLI, or returns null when it cannot be found.
     *
     * @param startDirectory directory to begin the manifest search from, typically the solution
     *   or project directory. Parent directories are searched too, matching `dotnet`'s behavior.
     * @param explicitPath a user-configured path; blank values are ignored.
     * @param pathLookup resolves a bare command name against the PATH, injected for testability.
     */
    fun resolve(
        startDirectory: File?,
        explicitPath: String?,
        pathLookup: (String) -> File?,
    ): SquillCliLocation? {
        explicitPath?.takeIf { it.isNotBlank() }?.let {
            return SquillCliLocation.Explicit(it.trim())
        }

        findManifestDirectory(startDirectory)?.let {
            return SquillCliLocation.LocalTool(it)
        }

        return pathLookup(TOOL_COMMAND_NAME)?.let { SquillCliLocation.OnPath(it) }
    }

    /**
     * The real PATH lookup, for callers that aren't injecting a fake.
     *
     * Also probes the .NET global-tool directory explicitly: `~/.dotnet/tools` is added to the
     * shell profile by `dotnet tool install`, but a GUI-launched IDE often does not inherit it,
     * so a tool the user installed correctly would otherwise look missing.
     */
    fun lookupOnPath(command: String): File? {
        val exeNames = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            listOf("$command.exe", "$command.cmd", command)
        } else {
            listOf(command)
        }

        val pathDirectories = System.getenv("PATH")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val candidateDirectories = pathDirectories.map { File(it) } +
            File(System.getProperty("user.home"), ".dotnet/tools")

        return candidateDirectories.asSequence()
            .flatMap { directory -> exeNames.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    /**
     * Explains why [resolve] would fail, or returns null when the CLI is usable.
     *
     * Separate from [resolve] so the hot path stays a simple nullable lookup while the error path
     * can afford the extra filesystem probing needed to tell the failures apart.
     *
     * @param isToolRestored checks whether a manifest's tools have actually been restored;
     *   injected for testability. Defaults to probing the conventional restore location.
     */
    fun diagnose(
        startDirectory: File?,
        explicitPath: String?,
        pathLookup: (String) -> File?,
        isToolRestored: (File) -> Boolean = ::isToolRestoredAt,
    ): SquillCliProblem? {
        explicitPath?.takeIf { it.isNotBlank() }?.let { configured ->
            val path = configured.trim()
            return if (File(path).exists()) null else SquillCliProblem.ExplicitPathMissing(path)
        }

        findManifestDirectory(startDirectory)?.let { manifestDirectory ->
            return if (isToolRestored(manifestDirectory)) {
                null
            } else {
                SquillCliProblem.ManifestNotRestored(manifestDirectory)
            }
        }

        if (pathLookup(TOOL_COMMAND_NAME) != null) return null

        // No manifest declared Squill, but one may still exist nearby — if so, installing into
        // that manifest is a better suggestion than a global install.
        return SquillCliProblem.NotInstalled(findAnyManifestDirectory(startDirectory))
    }

    /**
     * Whether `dotnet tool restore` has populated the manifest's tools.
     *
     * The restore location isn't part of the SDK's public contract, so this is a best-effort
     * probe: a false negative merely surfaces a "run tool restore" hint that is harmless to
     * follow when the tool is in fact restored.
     */
    private fun isToolRestoredAt(manifestDirectory: File): Boolean =
        File(manifestDirectory, ".config/.tools").isDirectory ||
            File(System.getProperty("user.home"), ".nuget/packages/squill").isDirectory

    /** The nearest directory containing a tool manifest, whether or not it lists Squill. */
    private fun findAnyManifestDirectory(startDirectory: File?): File? =
        generateSequence(startDirectory?.absoluteFile) { it.parentFile }
            .firstOrNull { File(it, MANIFEST_RELATIVE_PATH).isFile }

    /**
     * Walks up from [startDirectory] looking for a `.config/dotnet-tools.json` that lists the
     * Squill tool, returning the directory containing `.config`.
     *
     * A manifest that exists but doesn't list Squill is skipped rather than treated as a stop
     * condition — an outer manifest may still provide the tool.
     */
    private fun findManifestDirectory(startDirectory: File?): File? =
        generateSequence(startDirectory?.absoluteFile) { it.parentFile }
            .firstOrNull { directory ->
                val manifest = File(directory, MANIFEST_RELATIVE_PATH)
                manifest.isFile && manifestDeclaresSquill(manifest)
            }

    /**
     * True when the manifest's `tools` object contains a Squill entry. Package ids are
     * case-insensitive in NuGet, so the comparison is too.
     *
     * Any read or parse failure is reported as "not declared": a broken manifest should fall
     * through to the next resolution strategy rather than fail the whole lookup.
     */
    private fun manifestDeclaresSquill(manifest: File): Boolean {
        val contents = runCatching { manifest.readText() }.getOrNull() ?: return false
        val toolsBlock = TOOLS_BLOCK_REGEX.find(contents)?.groupValues?.get(1) ?: return false

        return TOOL_KEY_REGEX.findAll(toolsBlock)
            .any { it.groupValues[1].equals(TOOL_COMMAND_NAME, ignoreCase = true) }
    }
}

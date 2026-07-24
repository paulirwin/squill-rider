package io.github.paulirwin.squill.rider.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Resolution is exercised against a real temporary directory tree rather than a mocked
 * filesystem: the thing most likely to break is how a manifest is located by walking parent
 * directories, and that logic is only meaningful against actual paths.
 */
class SquillCliResolverTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun manifestAt(dir: File, contents: String = MANIFEST_WITH_SQUILL): File {
        val configDir = File(dir, ".config").apply { mkdirs() }
        return File(configDir, "dotnet-tools.json").apply { writeText(contents) }
    }

    @Test
    fun `an explicit override wins over everything else`() {
        val override = temp.newFile("my-squill")
        manifestAt(temp.root)

        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = override.absolutePath,
            pathLookup = { File("/usr/bin/squill") },
        )

        assertEquals(SquillCliLocation.Explicit(override.absolutePath), resolved)
    }

    @Test
    fun `a blank override is ignored rather than treated as a path`() {
        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = "   ",
            pathLookup = { File("/usr/bin/squill") },
        )

        assertEquals(SquillCliLocation.OnPath(File("/usr/bin/squill")), resolved)
    }

    @Test
    fun `a local tool manifest listing squill resolves to a dotnet-run invocation`() {
        manifestAt(temp.root)

        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { File("/usr/bin/squill") },
        )

        // The manifest must win over the PATH copy: a solution pinning a tool version expects
        // that version, not whatever happens to be installed globally.
        assertEquals(SquillCliLocation.LocalTool(temp.root), resolved)
    }

    @Test
    fun `a manifest is found by walking up from a nested start directory`() {
        manifestAt(temp.root)
        val nested = File(temp.root, "src/Database").apply { mkdirs() }

        val resolved = SquillCliResolver.resolve(
            startDirectory = nested,
            explicitPath = null,
            pathLookup = { null },
        )

        assertEquals(SquillCliLocation.LocalTool(temp.root), resolved)
    }

    @Test
    fun `a manifest without squill is ignored`() {
        manifestAt(temp.root, MANIFEST_WITHOUT_SQUILL)

        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { File("/usr/bin/squill") },
        )

        assertEquals(SquillCliLocation.OnPath(File("/usr/bin/squill")), resolved)
    }

    @Test
    fun `a malformed manifest does not throw`() {
        manifestAt(temp.root, "{ this is not json")

        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { File("/usr/bin/squill") },
        )

        assertEquals(SquillCliLocation.OnPath(File("/usr/bin/squill")), resolved)
    }

    @Test
    fun `the tool name is matched case-insensitively`() {
        // NuGet package ids are case-insensitive, and the manifest records the id as published.
        manifestAt(temp.root, MANIFEST_WITH_SQUILL.replace("\"squill\"", "\"Squill\""))

        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { null },
        )

        assertEquals(SquillCliLocation.LocalTool(temp.root), resolved)
    }

    @Test
    fun `resolution returns null when nothing is found`() {
        val resolved = SquillCliResolver.resolve(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { null },
        )

        assertNull(resolved)
    }

    // --- Missing CLI -----------------------------------------------------------------------
    //
    // Squill documents only `dotnet tool install --global`, and ships no tool manifest in its
    // own repo or templates — so "not installed" is the state every new user starts in, not a
    // rare failure. These cases pin the diagnosis the UI needs to offer a remedy.

    @Test
    fun `a missing CLI is diagnosed as not installed, with a manifest-aware remedy`() {
        val diagnosis = SquillCliResolver.diagnose(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { null },
        )

        assertEquals(SquillCliProblem.NotInstalled(manifestDirectory = null), diagnosis)
    }

    @Test
    fun `a manifest that lists squill but is not restored is diagnosed distinctly`() {
        // `dotnet tool restore` hasn't been run: the manifest names the tool, but resolution
        // through `dotnet` would fail. This is a different remedy from "install it".
        manifestAt(temp.root)

        val diagnosis = SquillCliResolver.diagnose(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { null },
            isToolRestored = { false },
        )

        assertEquals(SquillCliProblem.ManifestNotRestored(temp.root), diagnosis)
    }

    @Test
    fun `an explicit path that does not exist is reported rather than silently used`() {
        val missing = File(temp.root, "nope/squill").absolutePath

        val diagnosis = SquillCliResolver.diagnose(
            startDirectory = temp.root,
            explicitPath = missing,
            pathLookup = { null },
        )

        assertEquals(SquillCliProblem.ExplicitPathMissing(missing), diagnosis)
    }

    @Test
    fun `a resolvable CLI has no problem to report`() {
        val diagnosis = SquillCliResolver.diagnose(
            startDirectory = temp.root,
            explicitPath = null,
            pathLookup = { File("/usr/bin/squill") },
        )

        assertNull(diagnosis)
    }

    @Test
    fun `a local tool builds a dotnet command line with the working directory set`() {
        val location = SquillCliLocation.LocalTool(temp.root)

        assertEquals("dotnet", location.executable)
        assertEquals(listOf("squill"), location.argumentPrefix)
        assertEquals(temp.root, location.workingDirectory)
    }

    @Test
    fun `a path or explicit location invokes the executable directly`() {
        val onPath = SquillCliLocation.OnPath(File("/usr/bin/squill"))
        assertEquals("/usr/bin/squill", onPath.executable)
        assertTrue(onPath.argumentPrefix.isEmpty())
        assertNull(onPath.workingDirectory)

        val explicit = SquillCliLocation.Explicit("/opt/squill")
        assertEquals("/opt/squill", explicit.executable)
        assertTrue(explicit.argumentPrefix.isEmpty())
    }

    private companion object {
        const val MANIFEST_WITH_SQUILL = """
            {
              "version": 1,
              "isRoot": true,
              "tools": {
                "squill": { "version": "1.0.0", "commands": [ "squill" ] }
              }
            }
        """

        const val MANIFEST_WITHOUT_SQUILL = """
            {
              "version": 1,
              "isRoot": true,
              "tools": {
                "dotnet-ef": { "version": "9.0.0", "commands": [ "dotnet-ef" ] }
              }
            }
        """
    }
}

package io.github.paulirwin.squill.rider.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins which problems the plugin can fix for the user by running a command, versus which it can
 * only explain.
 *
 * The split is empirical, not stylistic: `dotnet tool install --global Squill` is the install
 * path Squill documents and it works, while installing into a local manifest reproducibly fails
 * with "Settings file 'DotnetToolSettings.xml' was not found in the package" (verified against
 * Squill 0.2.0 on SDK 10.0.201, fresh manifest, package and NuGet cache both intact). A button
 * that runs a command known to fail is worse than instructions, so local install is
 * instructions-only until that is understood upstream.
 */
class SquillCliRemedyTest {
    @Test
    fun `a missing CLI offers a runnable global install`() {
        val remedy = SquillCliRemedy.forProblem(SquillCliProblem.NotInstalled(manifestDirectory = null))

        assertTrue(remedy is SquillCliRemedy.RunnableInstall)
        val install = remedy as SquillCliRemedy.RunnableInstall
        assertEquals("dotnet", install.executable)
        assertEquals(listOf("tool", "install", "--global", "Squill"), install.arguments)
        // A global install is not directory-sensitive; forcing a working directory would only
        // couple the command to whichever project happened to trigger it.
        assertEquals(null, install.workingDirectory)
    }

    @Test
    fun `a nearby manifest does not downgrade the remedy to local install`() {
        // Even when a manifest exists, the offered button stays the global install: the local
        // install path is the one that fails. The manifest is surfaced as guidance instead.
        val manifestDir = File("/solution")
        val remedy = SquillCliRemedy.forProblem(SquillCliProblem.NotInstalled(manifestDir))

        assertTrue(remedy is SquillCliRemedy.RunnableInstall)
        val install = remedy as SquillCliRemedy.RunnableInstall
        assertEquals(listOf("tool", "install", "--global", "Squill"), install.arguments)
        assertTrue(
            "a nearby manifest should be mentioned so the user knows the local option exists",
            install.note?.contains("dotnet tool install Squill") == true,
        )
    }

    @Test
    fun `an unrestored manifest offers a runnable restore`() {
        // `dotnet tool restore` is distinct from install and is not affected by the local-install
        // failure, so it is safe to offer as a button.
        val manifestDir = File("/solution")
        val remedy = SquillCliRemedy.forProblem(SquillCliProblem.ManifestNotRestored(manifestDir))

        assertTrue(remedy is SquillCliRemedy.RunnableInstall)
        val restore = remedy as SquillCliRemedy.RunnableInstall
        assertEquals("dotnet", restore.executable)
        assertEquals(listOf("tool", "restore"), restore.arguments)
        // Restore resolves the manifest by walking up from the working directory, so it must run
        // in the manifest's own directory.
        assertEquals(manifestDir, restore.workingDirectory)
    }

    @Test
    fun `a missing configured path is not something the plugin can fix by running a command`() {
        val remedy = SquillCliRemedy.forProblem(SquillCliProblem.ExplicitPathMissing("/opt/nope"))

        assertTrue(remedy is SquillCliRemedy.Manual)
        assertTrue((remedy as SquillCliRemedy.Manual).message.contains("/opt/nope"))
    }

    @Test
    fun `no remedy claims to install into a local manifest`() {
        // Guards the empirical finding above: if someone later adds a local-install button, this
        // fails and sends them back to the comment explaining why it was excluded.
        val remedies = listOf(
            SquillCliRemedy.forProblem(SquillCliProblem.NotInstalled(null)),
            SquillCliRemedy.forProblem(SquillCliProblem.NotInstalled(File("/solution"))),
            SquillCliRemedy.forProblem(SquillCliProblem.ManifestNotRestored(File("/solution"))),
        )

        remedies.filterIsInstance<SquillCliRemedy.RunnableInstall>().forEach { remedy ->
            val isLocalInstall = remedy.arguments.containsAll(listOf("tool", "install")) &&
                !remedy.arguments.contains("--global")
            assertFalse(
                "local `dotnet tool install` is known to fail; it must not be offered as a button",
                isLocalInstall,
            )
        }
    }
}

package io.github.paulirwin.squill.rider.run

import com.intellij.execution.configurations.GeneralCommandLine
import io.github.paulirwin.squill.rider.cli.SquillCliCommand
import io.github.paulirwin.squill.rider.cli.SquillDeployOptions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how the platform passes a connection string to the process.
 *
 * Connection strings contain `;`, `=`, and often spaces — all shell metacharacters. The question
 * this answers is whether the plugin must quote them itself, or whether doing so would send
 * literal quote characters through to the CLI.
 */
class SquillCommandLineQuotingTest {
    private fun commandLineFor(connectionString: String): GeneralCommandLine {
        val arguments = SquillCliCommand.deploy(
            dacpacPath = "/proj/bin/Debug/My.dacpac",
            connectionString = connectionString,
            options = SquillDeployOptions(),
        )
        return GeneralCommandLine().withExePath("squill").also { it.addParameters(arguments) }
    }

    @Test
    fun `the argument reaches the process verbatim, without added quotes`() {
        // GeneralCommandLine passes an argument vector to the OS directly — there is no shell to
        // re-split it — so the CLI receives exactly what was put in. Quoting here would be a bug:
        // the quotes would arrive as part of the value.
        val connectionString = "Host=localhost;Port=55432;Database=db;Username=u;Password=p w"

        val parameters = commandLineFor(connectionString).parametersList.list

        assertEquals(connectionString, parameters[parameters.indexOf("--connection-string") + 1])
    }

    @Test
    fun `the displayed command line quotes for readability only`() {
        // commandLineString is a human-readable rendering, so the platform adds quotes there to
        // show where an argument begins and ends. That display quoting must not be mistaken for
        // something the plugin needs to do itself.
        val connectionString = "Host=localhost;Database=db;Password=p w"

        val rendered = commandLineFor(connectionString).commandLineString

        assertEquals(
            "the rendering is expected to quote the argument containing a space",
            true,
            rendered.contains("\"$connectionString\"") || rendered.contains("'$connectionString'"),
        )
    }
}

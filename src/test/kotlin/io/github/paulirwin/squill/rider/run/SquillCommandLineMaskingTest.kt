package io.github.paulirwin.squill.rider.run

import com.intellij.execution.configurations.GeneralCommandLine
import io.github.paulirwin.squill.rider.cli.SquillCliCommand
import io.github.paulirwin.squill.rider.cli.SquillDeployOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the console echo against leaking credentials.
 *
 * Lives in `src/test` rather than `src/unitTest` because it asserts against the platform's real
 * [GeneralCommandLine] rendering: the point is that what Rider *actually* prints is masked, which
 * a hand-rolled string would not prove.
 */
class SquillCommandLineMaskingTest {
    private val secret = "sup3r-s3cret"
    private val connectionString = "Host=localhost;Port=55432;Database=db;Username=u;Password=$secret"

    private fun deployCommandLine(): GeneralCommandLine {
        val arguments = SquillCliCommand.deploy(
            dacpacPath = "/proj/bin/Debug/My.dacpac",
            connectionString = connectionString,
            options = SquillDeployOptions(),
        )
        return GeneralCommandLine().withExePath("squill").also { it.addParameters(arguments) }
    }

    @Test
    fun `the platform's own rendering would leak the password`() {
        // Establishes the risk this masking exists to prevent: without intervention, the string
        // Rider echoes to the console contains the password verbatim.
        val rendered = deployCommandLine().commandLineString

        assertTrue(
            "precondition: the raw rendering is expected to contain the secret",
            rendered.contains(secret),
        )
    }

    @Test
    fun `the masked rendering hides the password but keeps the command readable`() {
        val masked = SquillCommandLineMasker.maskedCommandLine(deployCommandLine())

        assertFalse("the password must not appear in the console echo", masked.contains(secret))
        assertTrue("the verb should stay visible", masked.contains("deploy"))
        assertTrue("the dacpac path should stay visible", masked.contains("My.dacpac"))
        // The rest of the connection string is not secret and is useful when diagnosing a
        // connection failure, so only the password value is replaced.
        assertTrue("non-secret connection details should stay visible", masked.contains("Host=localhost"))
        assertTrue("the mask should be visible as such", masked.contains("Password=***"))
    }

    @Test
    fun `masking survives a quoted connection string`() {
        // A connection string containing a semicolon-quoted password is the case most likely to
        // defeat a naive regex, since the value is wrapped in double quotes.
        val quoted = """Host=localhost;Password="a;b";Username=u"""
        val arguments = SquillCliCommand.deploy("/x.dacpac", quoted, SquillDeployOptions())
        val commandLine = GeneralCommandLine().withExePath("squill")
            .also { it.addParameters(arguments) }

        val masked = SquillCommandLineMasker.maskedCommandLine(commandLine)

        assertFalse(masked.contains("a;b"))
        assertTrue(masked.contains("Password=***"))
    }

    @Test
    fun `the handler is given the masked rendering, while the process gets the real arguments`() {
        // The end-to-end guarantee: what Rider echoes is masked, but the argument vector handed
        // to the OS still carries the real password. Asserting only the masker in isolation
        // would not catch the launch path being wired to the unmasked string.
        val commandLine = deployCommandLine()

        val displayed = SquillCommandLineMasker.maskedCommandLine(commandLine)
        val actualArguments = commandLine.parametersList.list

        assertFalse("the echoed line must not carry the password", displayed.contains(secret))
        assertTrue(
            "the process must still receive the real connection string",
            actualArguments.contains(connectionString),
        )
    }

    @Test
    fun `a command line with no password is unchanged apart from rendering`() {
        val arguments = SquillCliCommand.deploy(
            "/x.dacpac", "Host=localhost;Database=db", SquillDeployOptions(),
        )
        val commandLine = GeneralCommandLine().withExePath("squill")
            .also { it.addParameters(arguments) }

        val masked = SquillCommandLineMasker.maskedCommandLine(commandLine)

        assertEquals(commandLine.commandLineString, masked)
    }
}

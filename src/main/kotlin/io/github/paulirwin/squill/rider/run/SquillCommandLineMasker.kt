package io.github.paulirwin.squill.rider.run

import com.intellij.execution.configurations.GeneralCommandLine
import io.github.paulirwin.squill.rider.cli.ConnectionStringMasker

/**
 * Renders a command line for display with credentials removed.
 *
 * Rider echoes the command it is about to run at the top of the console. That line is routinely
 * copied into bug reports and screenshots, so the password must not survive into it — while the
 * rest of the command stays readable enough to diagnose a failure.
 */
object SquillCommandLineMasker {
    /** The command line as it should be *shown*, never the one that is actually executed. */
    fun maskedCommandLine(commandLine: GeneralCommandLine): String =
        ConnectionStringMasker.mask(commandLine.commandLineString)
}

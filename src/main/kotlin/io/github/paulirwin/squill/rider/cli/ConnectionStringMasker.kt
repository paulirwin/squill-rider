package io.github.paulirwin.squill.rider.cli

/**
 * Replaces password values in an ADO.NET connection string with a placeholder.
 *
 * Rider echoes the command it is about to run into the console, and that line is routinely
 * copied into bug reports and screenshots. Only the password is removed — host, port, database,
 * and username are not secret and are what make a connection failure diagnosable.
 *
 * Pure string handling with no platform types, so it is covered by the fast `src/unitTest` source
 * set.
 */
object ConnectionStringMasker {
    const val MASK = "***"

    /**
     * Matches an ADO.NET password keyword and its value.
     *
     * The value alternation order matters: the quoted form is tried first so that a password
     * containing the `;` pair separator — which is precisely why it was quoted — is consumed
     * whole rather than truncated at the first semicolon. Inside quotes, a doubled `""` is an
     * escaped quote and must not terminate the value.
     *
     * Keywords are case-insensitive and tolerate whitespace around `=`, matching ADO.NET's own
     * parsing. `\b` on the keyword prevents matching a longer word that merely ends in
     * "password".
     */
    private val PASSWORD_REGEX = Regex(
        """\b(password|pwd)(\s*=\s*)("(?:[^"]|"")*"|[^;]*)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns [text] with every password value replaced by [MASK].
     *
     * Safe to apply to a whole command line, not just a bare connection string — the keyword
     * anchor is what locates the value.
     */
    fun mask(text: String): String =
        PASSWORD_REGEX.replace(text) { match ->
            val keyword = match.groupValues[1]
            val separator = match.groupValues[2]
            "$keyword$separator$MASK"
        }
}

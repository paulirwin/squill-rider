package io.github.paulirwin.squill.rider.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The console echo is routinely pasted into bug reports, so a password that survives it is a
 * credential leak. These cases pin the forms a password can take in an ADO.NET connection string.
 */
class ConnectionStringMaskerTest {
    @Test
    fun `a bare password value is replaced`() {
        assertEquals(
            "Host=localhost;Password=***;Username=u",
            ConnectionStringMasker.mask("Host=localhost;Password=hunter2;Username=u"),
        )
    }

    @Test
    fun `a quoted password value is replaced along with its quotes`() {
        // Quoting is how a password containing the ';' separator is carried, so this is exactly
        // the case a naive "up to the next semicolon" rule gets wrong.
        assertEquals(
            "Host=localhost;Password=***;Username=u",
            ConnectionStringMasker.mask("""Host=localhost;Password="a;b";Username=u"""),
        )
    }

    @Test
    fun `a doubled quote inside a quoted password does not end the value early`() {
        val masked = ConnectionStringMasker.mask("""Password="a""b;c";Host=x""")

        assertFalse(masked.contains("a\"\"b"))
        assertEquals("Password=***;Host=x", masked)
    }

    @Test
    fun `the keyword is matched case-insensitively and with spaces`() {
        // ADO.NET keywords are case-insensitive and tolerate whitespace around '='.
        assertEquals("***", ConnectionStringMasker.mask("PASSWORD=x").substringAfter("PASSWORD="))
        assertEquals("Pwd=***", ConnectionStringMasker.mask("Pwd=x"))
        assertEquals("password = ***", ConnectionStringMasker.mask("password = x"))
    }

    @Test
    fun `the password is masked wherever it appears in a longer string`() {
        // In practice the connection string is embedded in a full command line.
        val commandLine =
            """squill deploy /p/My.dacpac --connection-string Host=h;Password=s3cret;Database=d"""

        val masked = ConnectionStringMasker.mask(commandLine)

        assertFalse(masked.contains("s3cret"))
        assertEquals(
            "squill deploy /p/My.dacpac --connection-string Host=h;Password=***;Database=d",
            masked,
        )
    }

    @Test
    fun `non-secret keywords are left alone`() {
        // Everything except the password is useful for diagnosing a connection failure.
        val input = "Host=localhost;Port=5432;Database=db;Username=alice"

        assertEquals(input, ConnectionStringMasker.mask(input))
    }

    @Test
    fun `a value that merely contains the word password is not masked`() {
        val input = "Host=localhost;Database=password_store"

        assertEquals(input, ConnectionStringMasker.mask(input))
    }

    @Test
    fun `an empty password is still masked`() {
        // Masking an empty value reveals nothing, but leaving it unmasked would mean the regex
        // failed to match — better to normalize than to leak on the next input shape.
        assertEquals("Password=***;Host=x", ConnectionStringMasker.mask("Password=;Host=x"))
    }
}

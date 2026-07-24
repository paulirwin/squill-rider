package io.github.paulirwin.squill.rider.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataGrip stores JDBC URLs; Squill's CLI takes ADO.NET connection strings (Npgsql /
 * MySqlConnector). These assert the translation between them, including the escaping rules that
 * decide whether a password with a semicolon in it corrupts the whole string.
 */
class JdbcConnectionStringTest {
    @Test
    fun `postgres url maps to Npgsql keywords`() {
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://db.example.com:5433/analytics", "alice", "s3cret",
        )

        assertEquals(
            "Host=db.example.com;Port=5433;Database=analytics;Username=alice;Password=s3cret",
            ado,
        )
    }

    @Test
    fun `mysql url maps to MySqlConnector keywords`() {
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:mysql://localhost:3306/shop", "root", "pw",
        )

        assertEquals("Server=localhost;Port=3306;Database=shop;User ID=root;Password=pw", ado)
    }

    @Test
    fun `mariadb url is treated as mysql`() {
        val ado = JdbcConnectionString.toAdoNet("jdbc:mariadb://localhost/shop", "root", null)

        assertEquals("Server=localhost;Database=shop;User ID=root", ado)
    }

    @Test
    fun `the default port is omitted rather than guessed`() {
        // Omitting lets the driver apply its own default; inventing one here would silently
        // diverge from whatever the driver would have chosen.
        val ado = JdbcConnectionString.toAdoNet("jdbc:postgresql://localhost/mydb", null, null)

        assertEquals("Host=localhost;Database=mydb", ado)
    }

    @Test
    fun `a missing password is omitted, not emitted empty`() {
        // An empty Password= is not the same as no password: some drivers treat it as an
        // explicit empty credential and fail differently.
        val ado = JdbcConnectionString.toAdoNet("jdbc:postgresql://localhost/db", "bob", null)

        assertEquals("Host=localhost;Database=db;Username=bob", ado)
        assertTrue(!ado!!.contains("Password="))
    }

    @Test
    fun `values containing a semicolon are quoted`() {
        // A semicolon is the ADO.NET pair separator; unquoted it would truncate the string and
        // silently drop every following keyword.
        val ado = JdbcConnectionString.toAdoNet("jdbc:postgresql://localhost/db", "bob", "a;b")

        assertEquals("Host=localhost;Database=db;Username=bob;Password=\"a;b\"", ado)
    }

    @Test
    fun `values containing a double quote are escaped by doubling`() {
        val ado = JdbcConnectionString.toAdoNet("jdbc:postgresql://localhost/db", null, "a\"b")

        assertEquals("Host=localhost;Database=db;Password=\"a\"\"b\"", ado)
    }

    @Test
    fun `unrecognized query parameters are ignored`() {
        // DataGrip appends its own parameters; translating each to an ADO.NET equivalent is a
        // far wider surface than this needs, so only credentials are read out.
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost:5432/db?ssl=true&ApplicationName=DataGrip", null, null,
        )

        assertEquals("Host=localhost;Port=5432;Database=db", ado)
    }

    // --- Credentials in the query string ---------------------------------------------------
    //
    // A user who pastes a full JDBC URL with credentials into DataGrip, rather than filling the
    // separate user/password fields, would otherwise have them silently dropped and the CLI
    // would be invoked without a password.

    @Test
    fun `user and password are read from the query string when not supplied separately`() {
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost:55432/squill_test?user=squill&password=squill", null, null,
        )

        assertEquals(
            "Host=localhost;Port=55432;Database=squill_test;Username=squill;Password=squill",
            ado,
        )
    }

    @Test
    fun `query string credentials work for mysql keywords too`() {
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:mariadb://localhost:53306/squill_test?user=squill&password=squill", null, null,
        )

        assertEquals(
            "Server=localhost;Port=53306;Database=squill_test;User ID=squill;Password=squill",
            ado,
        )
    }

    @Test
    fun `explicitly supplied credentials win over the query string`() {
        // The data source's own fields are the authoritative credentials; the query string is
        // only a fallback for when they are empty.
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost/db?user=fromurl&password=fromurl", "real", "realpw",
        )

        assertEquals("Host=localhost;Database=db;Username=real;Password=realpw", ado)
    }

    @Test
    fun `each credential falls back independently`() {
        // A data source commonly stores the username but leaves the password to the URL.
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost/db?password=fromurl", "real", null,
        )

        assertEquals("Host=localhost;Database=db;Username=real;Password=fromurl", ado)
    }

    @Test
    fun `percent-encoded credentials are decoded`() {
        // Query values are percent-encoded, so a password with reserved characters arrives
        // encoded and must be decoded before it reaches the CLI.
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost/db?user=a%40b&password=p%40ss%3Aword", null, null,
        )

        assertEquals("Host=localhost;Database=db;Username=a@b;Password=p@ss:word", ado)
    }

    @Test
    fun `a decoded credential containing a semicolon is still quoted`() {
        // Decoding happens before escaping, so a percent-encoded semicolon must not be able to
        // smuggle a pair separator into the connection string.
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost/db?password=a%3Bb", null, null,
        )

        assertEquals("Host=localhost;Database=db;Password=\"a;b\"", ado)
    }

    @Test
    fun `blank query credentials are treated as absent`() {
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost/db?user=&password=", null, null,
        )

        assertEquals("Host=localhost;Database=db", ado)
    }

    @Test
    fun `the local test containers' urls translate correctly`() {
        // The exact URLs used to test the plugin against local Docker containers, pinned so the
        // round trip that motivated query-string fallback keeps working.
        assertEquals(
            "Host=localhost;Port=55432;Database=squill_test;Username=squill;Password=squill",
            JdbcConnectionString.toAdoNet(
                "jdbc:postgresql://localhost:55432/squill_test?user=squill&password=squill",
                null,
                null,
            ),
        )

        assertEquals(
            "Server=localhost;Port=53306;Database=squill_test;User ID=squill;Password=squill",
            JdbcConnectionString.toAdoNet(
                "jdbc:mariadb://localhost:53306/squill_test?user=squill&password=squill",
                null,
                null,
            ),
        )
    }

    @Test
    fun `the username parameter spelling is also accepted`() {
        // Postgres JDBC accepts `user`; some tooling emits `username`.
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost/db?username=squill", null, null,
        )

        assertEquals("Host=localhost;Database=db;Username=squill", ado)
    }

    @Test
    fun `an unsupported scheme returns null rather than a wrong guess`() {
        // Squill only targets Postgres and MariaDB/MySQL; emitting a plausible-looking string for
        // anything else would fail deep inside the CLI instead of at configuration time.
        assertNull(JdbcConnectionString.toAdoNet("jdbc:sqlserver://localhost;database=x", null, null))
        assertNull(JdbcConnectionString.toAdoNet("not a url at all", null, null))
        assertNull(JdbcConnectionString.toAdoNet("", null, null))
    }

    @Test
    fun `a url without a database still produces host information`() {
        val ado = JdbcConnectionString.toAdoNet("jdbc:postgresql://localhost:5432/", null, null)

        assertEquals("Host=localhost;Port=5432", ado)
    }
}

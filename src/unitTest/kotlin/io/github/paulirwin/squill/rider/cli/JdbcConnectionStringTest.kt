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
    fun `query parameters in the jdbc url are ignored`() {
        val ado = JdbcConnectionString.toAdoNet(
            "jdbc:postgresql://localhost:5432/db?ssl=true&ApplicationName=DataGrip", null, null,
        )

        assertEquals("Host=localhost;Port=5432;Database=db", ado)
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

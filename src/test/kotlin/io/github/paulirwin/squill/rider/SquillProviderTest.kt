package io.github.paulirwin.squill.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SquillProviderTest {

    @Test
    fun resolvesPostgresqlNames() {
        assertEquals(SquillProvider.POSTGRESQL, SquillProvider.fromProviderName("Postgresql"))
        assertEquals(SquillProvider.POSTGRESQL, SquillProvider.fromProviderName("PostgreSQL"))
        assertEquals(SquillProvider.POSTGRESQL, SquillProvider.fromProviderName("postgres"))
    }

    @Test
    fun resolvesMariaDbAndMySql() {
        assertEquals(SquillProvider.MARIADB, SquillProvider.fromProviderName("MariaDb"))
        assertEquals(SquillProvider.MARIADB, SquillProvider.fromProviderName("MARIADB"))
        assertEquals(SquillProvider.MYSQL, SquillProvider.fromProviderName("MySql"))
        assertEquals(SquillProvider.MYSQL, SquillProvider.fromProviderName("mysql"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(SquillProvider.POSTGRESQL, SquillProvider.fromProviderName("  Postgresql \n"))
    }

    @Test
    fun returnsNullForUnknownOrBlank() {
        assertNull(SquillProvider.fromProviderName("SqlServer"))
        assertNull(SquillProvider.fromProviderName(""))
        assertNull(SquillProvider.fromProviderName("   "))
        assertNull(SquillProvider.fromProviderName(null))
    }

    @Test
    fun defaultIsPostgresql() {
        assertEquals(SquillProvider.POSTGRESQL, SquillProvider.DEFAULT)
    }

    @Test
    fun mapsToSqlDialectIds() {
        // These IDs must match the bundled com.intellij.database dialect Language IDs.
        assertEquals("PostgreSQL", SquillProvider.POSTGRESQL.sqlDialectId)
        assertEquals("MariaDB", SquillProvider.MARIADB.sqlDialectId)
        assertEquals("MySQL", SquillProvider.MYSQL.sqlDialectId)
    }
}

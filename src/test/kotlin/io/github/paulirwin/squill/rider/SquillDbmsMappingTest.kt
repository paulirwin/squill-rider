package io.github.paulirwin.squill.rider

import com.intellij.database.Dbms
import org.junit.Assert.assertEquals
import org.junit.Test

class SquillDbmsMappingTest {

    @Test
    fun mapsEachProviderToItsDbms() {
        assertEquals(Dbms.POSTGRES, SquillProvider.POSTGRESQL.toDbms())
        assertEquals(Dbms.MARIA, SquillProvider.MARIADB.toDbms())
        assertEquals(Dbms.MYSQL, SquillProvider.MYSQL.toDbms())
    }
}

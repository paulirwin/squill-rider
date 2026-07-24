package io.github.paulirwin.squill.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SquillProjectFileTest {

    @Test
    fun readsProviderNameFromPropertyGroup() {
        val content = """
            <Project>
              <PropertyGroup>
                <SquillProviderName>MariaDb</SquillProviderName>
                <SquillTargetVersion>11</SquillTargetVersion>
              </PropertyGroup>
            </Project>
        """.trimIndent()
        assertEquals("MariaDb", SquillProjectFile.readProviderName(content))
    }

    @Test
    fun readProviderNameTrimsInnerWhitespace() {
        val content = "<SquillProviderName>  Postgresql  </SquillProviderName>"
        assertEquals("Postgresql", SquillProjectFile.readProviderName(content))
    }

    @Test
    fun readProviderNameIsNullWhenAbsentOrEmpty() {
        assertNull(SquillProjectFile.readProviderName("<Project></Project>"))
        assertNull(SquillProjectFile.readProviderName("<SquillProviderName></SquillProviderName>"))
        assertNull(SquillProjectFile.readProviderName("<SquillProviderName>   </SquillProviderName>"))
    }

    @Test
    fun resolveProviderDefaultsToPostgresqlWhenAbsent() {
        assertEquals(SquillProvider.POSTGRESQL, SquillProjectFile.resolveProvider("<Project/>"))
    }

    @Test
    fun resolveProviderReadsDeclaredProvider() {
        val content = "<Project><PropertyGroup><SquillProviderName>MySql</SquillProviderName></PropertyGroup></Project>"
        assertEquals(SquillProvider.MYSQL, SquillProjectFile.resolveProvider(content))
    }

    @Test
    fun resolveProviderDefaultsWhenNameUnrecognized() {
        val content = "<SquillProviderName>SqlServer</SquillProviderName>"
        assertEquals(SquillProvider.POSTGRESQL, SquillProjectFile.resolveProvider(content))
    }
}

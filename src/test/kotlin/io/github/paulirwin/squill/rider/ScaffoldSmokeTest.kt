package io.github.paulirwin.squill.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke test proving the scaffold's resources are wired up: the message bundle resolves
 * and returns its value. Real functional tests (provider detection, dialect mapping)
 * arrive with those features in follow-up PRs.
 */
class ScaffoldSmokeTest {

    @Test
    fun bundleResolvesName() {
        assertEquals("Squill", SquillBundle.message("name"))
    }

    @Test
    fun pluginDescriptorIsPresent() {
        val descriptor = javaClass.classLoader.getResource("META-INF/plugin.xml")
        assertTrue("plugin.xml should be on the classpath", descriptor != null)
    }
}

package io.github.paulirwin.squill.rider

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * On project open, applies each `.squillproj`'s SQL dialect to its folder so the project's `.sql`
 * files highlight with the provider-specific dialect.
 */
class SquillSqlDialectStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<SquillSqlDialectService>().applyDialectsToAllSquillProjects()
    }
}

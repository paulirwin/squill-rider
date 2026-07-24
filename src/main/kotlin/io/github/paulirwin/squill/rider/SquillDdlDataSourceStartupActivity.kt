package io.github.paulirwin.squill.rider

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * On project open, builds an offline DDL data source per `.squillproj` so cross-file SQL object
 * references (tables, columns declared in sibling `.sql` files) resolve.
 */
class SquillDdlDataSourceStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<SquillDdlDataSourceService>().syncAllSquillProjects()
    }
}

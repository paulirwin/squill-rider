package io.github.paulirwin.squill.rider

import com.intellij.database.Dbms
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.sql.database.SqlDataSourceImpl
import com.intellij.sql.database.SqlDataSourceManager
import com.jetbrains.rider.projectView.workspace.findProjects

/**
 * Backs each `.squillproj` with an offline **DDL data source** (a [SqlDataSourceImpl]) built from
 * the project's own `.sql` files, so SQL object references resolve across files without a live
 * database connection — e.g. `REFERENCES author(author_id)` where `author` is declared in another
 * `.sql` file. This is the same mechanism Rider's bundled sqlproj plugin uses for SSDT projects.
 *
 * One data source per `.squillproj`, each with its provider's dialect. Managed data sources are
 * reconciled on each run (found by [MANAGED_MARKER] in their comment and updated in place) so the
 * persisted `sqlDataSources.xml` never accumulates duplicates.
 */
@Service(Service.Level.PROJECT)
class SquillDdlDataSourceService(private val project: Project) {

    /** Build or refresh a DDL data source for every `.squillproj` LOADED in the solution. */
    suspend fun syncAllSquillProjects() {
        // Enumerate actual loaded projects from the workspace model — NOT every .squillproj file on
        // disk. A `.squillproj` can also appear merely as an included item in another project (e.g.
        // the Squill.Templates scaffold's Database.squillproj); those must not get a data source.
        val projectFiles = readAction {
            WorkspaceModel.getInstance(project).findProjects()
                .mapNotNull { entity -> entity.url?.let { VirtualFileManager.getInstance().findFileByUrl(it.url) } }
                .filter { it.extension.equals(SQUILLPROJ_EXTENSION_NO_DOT, ignoreCase = true) }
        }
        projectFiles.forEach { syncProject(it) }
        removeOrphanedDataSources(projectFiles.map(::dataSourceName).toSet())
    }

    /**
     * Removes plugin-managed data sources whose `.squillproj` is no longer a loaded project — e.g.
     * one persisted from a previous session, or a project removed from the solution.
     */
    private suspend fun removeOrphanedDataSources(liveNames: Set<String>) {
        val manager = SqlDataSourceManager.getInstance(project)
        val orphans = manager.dataSources.filter { it.comment == MANAGED_MARKER && it.name !in liveNames }
        if (orphans.isEmpty()) return
        writeAction {
            orphans.forEach {
                manager.removeDataSource(it)
                LOG.info("Removed orphaned DDL data source '${it.name}'")
            }
        }
    }

    /**
     * Creates or updates the DDL data source for a single `.squillproj`, pointing it at the `.sql`
     * files under the project directory and setting the dialect from `<SquillProviderName>`.
     */
    suspend fun syncProject(projectFile: VirtualFile) {
        val projectDir = projectFile.parent ?: return

        val content = runCatching { readAction { projectFile.readTextViaCharset() } }.getOrNull() ?: return
        val provider = SquillProjectFile.resolveProvider(content)
        val dbms = provider.toDbms()

        val sqlUrls = readAction { collectSqlFileUrls(projectDir) }
        if (sqlUrls.isEmpty()) return

        val dataSourceName = dataSourceName(projectFile)
        val manager = SqlDataSourceManager.getInstance(project)

        writeAction {
            val existing = findManagedDataSource(dataSourceName)
            val ds = existing ?: SqlDataSourceImpl(dataSourceName, project, null).also {
                it.comment = MANAGED_MARKER
                manager.addDataSource(it)
            }
            ds.setDefinedDbms(dbms)
            // Anchor scope/layout relativization at the project directory (like sqlproj does).
            ds.setOutputPath(projectDir.path)
            // Squill organizes .sql files in folders (Tables/, Views/, Programmability/) that are
            // purely organizational — the objects are unqualified (CREATE TABLE author) and all
            // live in the default schema. The DEFAULT DDL layout ("File per object by schema")
            // instead derives a schema from each file's top folder, so a view in Views/ can't see
            // a table in Tables/. "File per object" has no such fileScope rule, so everything
            // resolves flat in the default schema and unqualified cross-folder references work.
            ds.setOutputLayout(FLAT_LAYOUT)
            // setAutoSync(true) makes the platform (re)build the model from these files whenever
            // the url set or the files change — no explicit sync call needed (and the explicit
            // DataSourceSyncManager path hits an IllegalAccessError from a plugin classloader).
            ds.setAutoSync(true)
            ds.urls = sqlUrls
        }
        LOG.info("Configured DDL data source '$dataSourceName' ($provider, ${sqlUrls.size} files)")
    }

    /** Finds a plugin-managed data source by name (dedup key), or null. */
    private fun findManagedDataSource(name: String): SqlDataSourceImpl? =
        SqlDataSourceManager.getInstance(project).dataSources
            .firstOrNull { it.name == name && it.comment == MANAGED_MARKER }

    /** All `.sql` files under [dir] (recursive), as VFS URLs. Deploy scripts included; harmless. */
    private fun collectSqlFileUrls(dir: VirtualFile): List<String> {
        val urls = mutableListOf<String>()
        com.intellij.openapi.vfs.VfsUtilCore.iterateChildrenRecursively(dir, null) { file ->
            if (!file.isDirectory && file.extension.equals(SQL_EXTENSION_NO_DOT, ignoreCase = true)) {
                urls.add(file.url)
            }
            true
        }
        return urls
    }

    private fun dataSourceName(projectFile: VirtualFile): String =
        "Squill: ${projectFile.nameWithoutExtension}"

    private companion object {
        val LOG = logger<SquillDdlDataSourceService>()
        const val SQUILLPROJ_EXTENSION_NO_DOT = "squillproj"
        const val SQL_EXTENSION_NO_DOT = "sql"

        // A bundled DDL layout with no folder->schema rule, so all files resolve in the default
        // schema. The default layout ("File per object by schema.groovy") maps folder->schema.
        const val FLAT_LAYOUT = "File per object.groovy"

        // Stored in the data source's comment so we can recognize and reconcile our own managed
        // data sources without clobbering ones the user created.
        const val MANAGED_MARKER = "Managed by the Squill plugin"
    }
}

/** Maps a [SquillProvider] to the IntelliJ database [Dbms] whose SQL model to build. */
internal fun SquillProvider.toDbms(): Dbms = when (this) {
    SquillProvider.POSTGRESQL -> Dbms.POSTGRES
    SquillProvider.MARIADB -> Dbms.MARIA
    SquillProvider.MYSQL -> Dbms.MYSQL
}

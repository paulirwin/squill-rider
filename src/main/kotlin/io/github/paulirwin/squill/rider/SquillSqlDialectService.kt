package io.github.paulirwin.squill.rider

import com.intellij.database.util.SqlDialects
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.sql.dialects.SqlDialectMappings

/**
 * Applies the SQL dialect matching each `.squillproj`'s `<SquillProviderName>` to that project's
 * folder, so Rider highlights and completes the project's `.sql` files with the right dialect
 * (PostgreSQL, MariaDB, or MySQL). The mapping is set on the project *directory*; Rider's
 * per-file mappings cascade to descendant files.
 */
@Service(Service.Level.PROJECT)
class SquillSqlDialectService(private val project: Project) {

    /** Scan all `.squillproj` files in the project and apply their dialect to their folders. */
    suspend fun applyDialectsToAllSquillProjects() {
        // The filename index requires a read action.
        val projectFiles = readAction {
            FilenameIndex.getAllFilesByExt(
                project,
                SQUILLPROJ_EXTENSION_NO_DOT,
                GlobalSearchScope.projectScope(project),
            ).toList()
        }
        projectFiles.forEach { applyDialectForProjectFile(it) }
    }

    /**
     * Reads the provider from [projectFile] (a `.squillproj`) and maps the resolved SQL dialect
     * onto the file's parent directory. No-op if the file can't be read or the dialect is missing.
     */
    suspend fun applyDialectForProjectFile(projectFile: VirtualFile) {
        val projectDir = projectFile.parent ?: return

        val content = runCatching { readAction { projectFile.readTextViaCharset() } }.getOrNull() ?: return
        val provider = SquillProjectFile.resolveProvider(content)

        val dialect = SqlDialects.findDialectById(provider.sqlDialectId)
        if (dialect == null) {
            LOG.warn("No SQL dialect '${provider.sqlDialectId}' for provider $provider (${projectFile.name})")
            return
        }

        // setMapping mutates a persistent project component, so it needs a write action.
        writeAction {
            SqlDialectMappings.getInstance(project).setMapping(projectDir, dialect)
        }
        LOG.info("Mapped ${projectDir.name} to $provider (${provider.sqlDialectId})")
    }

    private companion object {
        val LOG = logger<SquillSqlDialectService>()

        // FilenameIndex.getAllFilesByExt expects the extension without the leading dot.
        const val SQUILLPROJ_EXTENSION_NO_DOT = "squillproj"
    }
}

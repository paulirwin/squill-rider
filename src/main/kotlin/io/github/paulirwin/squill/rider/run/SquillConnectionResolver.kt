package io.github.paulirwin.squill.rider.run

import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import io.github.paulirwin.squill.rider.cli.JdbcConnectionString

/**
 * Turns a stored DataGrip data source id into an ADO.NET connection string for the Squill CLI.
 *
 * Credentials live in the platform's credential store and are read only at launch, so nothing
 * secret is ever written to the run configuration's XML.
 */
object SquillConnectionResolver {
    /** Every user-configured data source, for the run configuration's picker. */
    fun listDataSources(project: Project): List<LocalDataSource> =
        DataSourceStorage.getProjectStorage(project).dataSources

    fun findDataSource(project: Project, id: String?): LocalDataSource? =
        id?.takeIf { it.isNotBlank() }?.let { wanted ->
            listDataSources(project).firstOrNull { it.uniqueId == wanted }
        }

    /**
     * Builds the connection string to pass to `--connection-string`.
     *
     * Returns null when the data source is gone or its URL can't be translated, so the caller can
     * report a useful error instead of launching the CLI with a malformed argument.
     *
     * The password is fetched separately from [DatabaseCredentials] and may legitimately be null
     * — the user may have chosen "Ask each time" or never saved one. In that case the connection
     * string is emitted without a password, and the database itself decides whether that works.
     */
    fun buildConnectionString(project: Project, dataSourceId: String?): String? {
        val dataSource = findDataSource(project, dataSourceId) ?: return null
        val url = dataSource.url?.takeIf { it.isNotBlank() } ?: return null

        // getCredentials(DatabaseConnectionPoint) rather than the deprecated
        // getPassword(DasDataSource). Wrapped because retrieval can prompt for the master
        // password or fail outright; a null password is a legitimate outcome when the user chose
        // "Ask each time" or never saved one.
        val password = runCatching {
            DatabaseCredentials.getInstance().getCredentials(dataSource)?.getPasswordAsString()
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        return JdbcConnectionString.toAdoNet(
            jdbcUrl = url,
            username = dataSource.username?.takeIf { it.isNotBlank() },
            password = password,
        )
    }
}

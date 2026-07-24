package io.github.paulirwin.squill.rider

/**
 * The database providers a Squill project can target, as selected by the `<SquillProviderName>`
 * MSBuild property. Kept independent of the IntelliJ SQL dialect classes so provider resolution
 * is pure and unit-testable; the mapping to a concrete `SqlLanguageDialect` lives at the call site.
 */
enum class SquillProvider(
    /**
     * The IntelliJ SQL dialect Language ID this provider maps to, as registered by the bundled
     * `com.intellij.database` dialects (verified against Rider's `PgDialect`/`MysqlDialect`/
     * `MariaDialect`). Used with `SqlDialects.findDialectById(...)` so the plugin need not
     * hard-reference the dialect classes (which live in optionally-loaded modules).
     */
    val sqlDialectId: String,
) {
    POSTGRESQL("PostgreSQL"),
    MARIADB("MariaDB"),
    MYSQL("MySQL"),
    ;

    companion object {
        /**
         * The provider Squill assumes when `<SquillProviderName>` is absent (see `Squill.Sdk`'s
         * `Sdk.props`, which defaults the property to `Postgresql`).
         */
        val DEFAULT = POSTGRESQL

        /**
         * Resolves a `<SquillProviderName>` value to a [SquillProvider], case-insensitively and
         * trimming surrounding whitespace. Returns null for an unrecognized name so the caller can
         * decide whether to fall back to [DEFAULT] or leave the dialect untouched.
         *
         * Recognized names mirror Squill's project template: `Postgresql`, `MariaDb`, `MySql`.
         */
        fun fromProviderName(providerName: String?): SquillProvider? =
            when (providerName?.trim()?.lowercase()) {
                "postgresql", "postgres" -> POSTGRESQL
                "mariadb" -> MARIADB
                "mysql" -> MYSQL
                else -> null
            }
    }
}

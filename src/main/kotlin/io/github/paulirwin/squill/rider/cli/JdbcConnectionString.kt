package io.github.paulirwin.squill.rider.cli

/**
 * Translates a JDBC URL (what DataGrip stores) into an ADO.NET connection string (what the Squill
 * CLI's `--connection-string` expects).
 *
 * Pure string handling with no platform types, so it is covered by the fast `src/unitTest` source
 * set. The keyword sets follow the drivers Squill actually uses — Npgsql for PostgreSQL and
 * MySqlConnector for MariaDB/MySQL.
 */
object JdbcConnectionString {
    /**
     * Converts [jdbcUrl] plus optional credentials to an ADO.NET connection string, or null when
     * the URL is not one Squill can target.
     *
     * Returning null rather than a best guess is deliberate: an unrecognized scheme surfaces as a
     * configuration-time error, instead of a plausible-looking string that fails deep inside the
     * CLI against a database it was never going to support.
     */
    fun toAdoNet(jdbcUrl: String, username: String?, password: String?): String? {
        val parsed = parse(jdbcUrl) ?: return null

        val pairs = buildList {
            when (parsed.dialect) {
                Dialect.POSTGRES -> {
                    add("Host" to parsed.host)
                    parsed.port?.let { add("Port" to it) }
                    parsed.database?.let { add("Database" to it) }
                    username?.let { add("Username" to it) }
                }

                Dialect.MYSQL -> {
                    add("Server" to parsed.host)
                    parsed.port?.let { add("Port" to it) }
                    parsed.database?.let { add("Database" to it) }
                    username?.let { add("User ID" to it) }
                }
            }
            // Omitted entirely when absent: an empty `Password=` reads as an explicit empty
            // credential to some drivers, which fails differently from supplying none.
            password?.let { add("Password" to it) }
        }

        return pairs.joinToString(";") { (key, value) -> "$key=${escapeValue(value)}" }
    }

    private enum class Dialect { POSTGRES, MYSQL }

    private data class ParsedUrl(
        val dialect: Dialect,
        val host: String,
        val port: String?,
        val database: String?,
    )

    /**
     * Matches `jdbc:<scheme>://<host>[:<port>][/<database>][?<params>]`.
     *
     * Query parameters are captured only so they can be discarded — DataGrip appends its own
     * (`ApplicationName`, ssl settings), and forwarding them would mean translating each into its
     * ADO.NET equivalent, which is a much larger surface than this feature needs.
     */
    private val URL_REGEX = Regex(
        """^jdbc:(postgresql|mysql|mariadb)://([^:/?]+)(?::(\d+))?(?:/([^?]*))?(?:\?.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    private fun parse(jdbcUrl: String): ParsedUrl? {
        val match = URL_REGEX.find(jdbcUrl.trim()) ?: return null
        val (scheme, host, port, database) = match.destructured

        return ParsedUrl(
            // MariaDB and MySQL share MySqlConnector, so they share keywords.
            dialect = if (scheme.equals("postgresql", ignoreCase = true)) {
                Dialect.POSTGRES
            } else {
                Dialect.MYSQL
            },
            host = host,
            port = port.takeIf { it.isNotBlank() },
            database = database.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Quotes a value that would otherwise break the `key=value;` grammar.
     *
     * A semicolon is the pair separator, so an unquoted one truncates the connection string and
     * silently drops every keyword after it — most likely to occur in a generated password.
     * Embedded double quotes are escaped by doubling, per the ADO.NET rules.
     */
    private fun escapeValue(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\'' || it == '=' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}

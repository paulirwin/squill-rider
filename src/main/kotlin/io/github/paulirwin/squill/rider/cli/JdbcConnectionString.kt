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

        // Credentials supplied by the caller (the data source's own fields) are authoritative;
        // the URL's query string is only a fallback, for the common case of pasting a complete
        // JDBC URL and leaving the separate user/password fields empty. Each falls back
        // independently, since storing the username but not the password is normal.
        val effectiveUsername = username?.takeIf { it.isNotBlank() } ?: parsed.queryUsername
        val effectivePassword = password?.takeIf { it.isNotBlank() } ?: parsed.queryPassword

        val pairs = buildList {
            when (parsed.dialect) {
                Dialect.POSTGRES -> {
                    add("Host" to parsed.host)
                    parsed.port?.let { add("Port" to it) }
                    parsed.database?.let { add("Database" to it) }
                    effectiveUsername?.let { add("Username" to it) }
                }

                Dialect.MYSQL -> {
                    add("Server" to parsed.host)
                    parsed.port?.let { add("Port" to it) }
                    parsed.database?.let { add("Database" to it) }
                    effectiveUsername?.let { add("User ID" to it) }
                }
            }
            // Omitted entirely when absent: an empty `Password=` reads as an explicit empty
            // credential to some drivers, which fails differently from supplying none.
            effectivePassword?.let { add("Password" to it) }
        }

        return pairs.joinToString(";") { (key, value) -> "$key=${escapeValue(value)}" }
    }

    private enum class Dialect { POSTGRES, MYSQL }

    private data class ParsedUrl(
        val dialect: Dialect,
        val host: String,
        val port: String?,
        val database: String?,
        val queryUsername: String?,
        val queryPassword: String?,
    )

    /**
     * Matches `jdbc:<scheme>://<host>[:<port>][/<database>][?<params>]`.
     *
     * Only credentials are read out of the query string. Other parameters are ignored: DataGrip
     * appends its own (`ApplicationName`, ssl settings), and translating each into its ADO.NET
     * equivalent is a much larger surface than this feature needs.
     */
    private val URL_REGEX = Regex(
        """^jdbc:(postgresql|mysql|mariadb)://([^:/?]+)(?::(\d+))?(?:/([^?]*))?(?:\?(.*))?$""",
        RegexOption.IGNORE_CASE,
    )

    private fun parse(jdbcUrl: String): ParsedUrl? {
        val match = URL_REGEX.find(jdbcUrl.trim()) ?: return null
        val (scheme, host, port, database, query) = match.destructured

        val parameters = parseQuery(query)

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
            // Postgres JDBC uses `user`; some tooling emits `username`.
            queryUsername = parameters["user"] ?: parameters["username"],
            queryPassword = parameters["password"],
        )
    }

    /**
     * Splits a query string into decoded parameters, keyed case-insensitively.
     *
     * Values are percent-decoded before use, so a password containing reserved characters
     * survives the round trip. Decoding happens before [escapeValue], so an encoded semicolon
     * cannot smuggle a pair separator into the resulting connection string.
     */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()

        return query.split('&')
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) return@mapNotNull null

                val name = parameter.substring(0, separator).lowercase()
                val value = percentDecode(parameter.substring(separator + 1))

                value.takeIf { it.isNotBlank() }?.let { name to it }
            }
            .toMap()
    }

    /**
     * Percent-decodes a query value.
     *
     * Hand-rolled rather than using `URLDecoder`, which additionally treats `+` as a space — a
     * rule from HTML form encoding that does not apply to JDBC URLs, and which would corrupt any
     * password containing a literal plus sign. A malformed escape is left as written rather than
     * throwing, since a credential is better passed through unchanged than rejected outright.
     */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value

        val decoded = StringBuilder(value.length)
        var index = 0

        while (index < value.length) {
            val character = value[index]
            val hex = if (character == '%' && index + 2 < value.length) {
                value.substring(index + 1, index + 3).toIntOrNull(16)
            } else {
                null
            }

            if (hex != null) {
                decoded.append(hex.toChar())
                index += 3
            } else {
                decoded.append(character)
                index++
            }
        }

        return decoded.toString()
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

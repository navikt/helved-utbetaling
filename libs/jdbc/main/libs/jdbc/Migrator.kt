package libs.jdbc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.connection
import libs.jdbc.concurrency.transaction
import libs.utils.Resource
import libs.utils.secureLog
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDateTime


/**
 * A database migration tool.
 *
 * @constructor will execute the '/migrations.sql' script on the datasource found on the coroutine context.
 *
 * @param locations is the classpath-location of the migration scripts.
 */
class Migrator(locations: List<File>) {
    constructor(location: File) : this(listOf(location))

    private var files = locations.flatMap { it.getSqlFiles() }

    init {
        runBlocking { executeSql(Resource.read("/migrations.sql")) }
    }

    /**
     * Execute all the migrations scripts.
     *
     * - Will skip existing migrations
     * - Will fail if scripts are changed
     * - Will fail if scripts are not versioned in sequence
     * - Will fail if migrated scripts are missing
     *
     * @throws IllegalStateException on failure.
     */
    suspend fun migrate() {
        val migrations = Migration.all().sortedBy { it.version }
        val candidates = files.map(MigrationWithFile::from).sortedBy { it.migration.version }
        val candidateWithMigration = candidates.associateWith { (candidate, _) ->
            migrations.find { it.version == candidate.version }
        }

        // Versions are sequenced
        val sequences = candidates.windowed(2, 1) { (a, b) -> isValidSequence(a.migration, b.migration) }
        if (sequences.any { !it }) {
            val order = candidates.map { it.migration.version }.joinToString()
            error("A version was not incremented by 1: registred order: $order")
        }

        // Checksum is not changed
        candidateWithMigration.forEach {
            validateChecksum(it.value, it.key)
        }

        // All applied migrations scripts are found in location
        migrations.forEach { migration ->
            if (migration.version !in candidates.map { it.migration.version }) {
                error("migration script applied is missing in location: ${migration.filename}")
            }
        }

        // Register new candidates
        candidateWithMigration.forEach { (candidate, migration) ->
            if (migration == null) {
                Migration.insert(candidate.migration)
            }
        }

        // To be migrated
        candidateWithMigration
            .filterValues { migration ->
                when (migration) {
                    null -> true
                    else -> !migration.success.also { if (it) jdbcLog.info("Migration [SKIP] ${migration.filename}") }
                }
            }
            .mapNotNull { (candidate, migration) ->
                try {
                    transaction {
                        migrate(candidate.file)

                        when (migration) {
                            null -> Migration.update(candidate.migration.copy(success = true))
                            else -> Migration.update(migration.copy(success = true, checksum = candidate.migration.checksum))
                        }

                        jdbcLog.info("Migration [DONE] ${candidate.file.name}")
                    }
                } catch (e: Exception) {
                    jdbcLog.info("Migration [FAIL] ${candidate.file.name}")
                    secureLog.error("Migration [FAIL] ${candidate.file.name}", e)
                }
            }
    }

    private fun validateChecksum(migration: Migration?, candidate: MigrationWithFile) {
        migration?.let {
            if (!migration.success) {
                return
            }

            if (migration.checksum != candidate.migration.checksum) {
                jdbcLog.info("${candidate.file.name}, checksum: ${candidate.migration.checksum} != previously migrated ${migration.checksum}")
                error("Checksum differs from existing migration: ${candidate.file.name}")
            }
        }
    }

    private suspend fun migrate(file: File) {
        transaction {
            val sql = file.readText(Charset.forName("UTF-8"))
            coroutineContext.connection.prepareStatement(sql).execute()
            jdbcLog.debug(sql)
        }
    }

    private fun isValidSequence(left: Migration, right: Migration): Boolean {
        return left.version == right.version - 1
    }

    private suspend fun executeSql(sql: String) =
        withContext(Jdbc.context) {
            transaction {
                coroutineContext.connection.prepareStatement(sql).execute()
                jdbcLog.debug(sql)
            }
        }
}

private fun File.getSqlFiles(): List<File> = listFiles()
    ?.let { files -> files.filter { it.extension == "sql" } }
    ?: error("Specified location is not a directory: $absolutePath")

internal data class MigrationWithFile(val migration: Migration, val file: File) {
    companion object {
        fun from(file: File) = MigrationWithFile(Migration.from(file), file)
    }
}

internal data class Migration(
    val version: Int,
    val filename: String,
    val checksum: String,
    val created_at: LocalDateTime,
    val success: Boolean,
) {
    companion object {
        fun from(file: File) = Migration(
            version = version(file.name),
            filename = file.name,
            checksum = checksum(file),
            created_at = LocalDateTime.now(),
            success = false,
        )

        fun from(rs: ResultSet) = Migration(
            version = rs.getInt("version"),
            filename = rs.getString("filename"),
            checksum = rs.getString("checksum"),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            success = rs.getBoolean("success"),
        )

        suspend fun all(): List<Migration> = transaction {
            coroutineContext.connection
                .prepareStatement("SELECT * FROM migrations")
                .use { stmt ->
                    secureLog.debug(stmt.toString())
                    stmt.executeQuery().map(::from)
                }
        }

        suspend fun insert(migration: Migration) = transaction {
            coroutineContext.connection
                .prepareStatement("INSERT INTO migrations (version, filename, checksum, created_at, success) VALUES (?, ?, ?, ?, ?)")
                .use { stmt ->
                    stmt.setInt(1, migration.version)
                    stmt.setString(2, migration.filename)
                    stmt.setString(3, migration.checksum)
                    stmt.setObject(4, migration.created_at)
                    stmt.setBoolean(5, migration.success)
                    secureLog.debug(stmt.toString())
                    stmt.executeUpdate()
                }
        }

        suspend fun update(migration: Migration) = transaction {
            coroutineContext.connection
                .prepareStatement("UPDATE migrations SET success = ?, checksum = ? WHERE version = ?")
                .use { stmt ->
                    stmt.setBoolean(1, migration.success)
                    stmt.setString(2, migration.checksum)
                    stmt.setInt(3, migration.version)
                    secureLog.debug(stmt.toString())
                    stmt.executeUpdate()
                }
        }
    }
}

private val DIGIT_PATTERN = Regex("\\d+")

private fun version(name: String): Int {
    return DIGIT_PATTERN.findAll(name)
        .map { it.value.toInt() }
        .firstOrNull() // first digit is the version
        ?: error("Version must be included in sql-filename: $name")
}

private fun checksum(file: File): String {
    val md = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(1024)
    val input = file.inputStream()
    while (true) {
        when (val read = input.read(buffer)) {
            -1 -> break
            else -> md.update(buffer, 0, read)
        }
    }
    return buildString {
        md.digest().forEach {
            append(String.format("%02x", it))
        }
    }
}

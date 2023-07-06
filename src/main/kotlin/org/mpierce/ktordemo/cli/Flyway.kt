package org.mpierce.ktordemo.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.natpryce.konfig.Configuration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(Flyway::class.java)

/**
 * Only holds subcommands for flyway
 */
class Flyway : CliktCommand() {
    private val config by option(help = "Directory of properties files to read").path()

    init {
        subcommands(
            Migrate(),
            Info(),
        )
    }

    override fun run() {
        currentContext.findOrSetObject {
            FlywayCommandConfig(buildConfig(config))
        }
    }
}

/**
 * Applies DB migrations.
 *
 * It is generally advisable to apply DB migrations manually as needed rather than at service startup. Migrating at
 * startup is convenient, up until you need to apply a migration that takes a long time to run, or until deployment
 * gets stuck because the migration lock gets wedged, etc.
 */
class Migrate : CliktCommand() {
    private val cmdConfig by requireObject<FlywayCommandConfig>()

    override fun run() {
        withFlyway(cmdConfig) { f ->
            val result = f.migrate()
            // quick and dirty logging; expand as needed
            logger.info("Executed ${result.migrationsExecuted} migrations${result.targetSchemaVersion?.let { ", now at $it" } ?: ""}")
        }
    }
}

class Info : CliktCommand() {
    private val cmdConfig by requireObject<FlywayCommandConfig>()

    override fun run() {
        withFlyway(cmdConfig) { f ->
            val result = f.info()

            fun printMigration(r: MigrationInfo) {
                logger.info("${r.version} - ${r.description} ${if (result.current()?.version == r.version) "(current)" else ""}")
            }

            if (result.applied().isNotEmpty()) {
                logger.info("Applied:")
                result.applied().forEach(::printMigration)
            }
            if (result.pending().isNotEmpty()) {
                logger.info("Pending:")
                result.pending().forEach(::printMigration)
            }
        }
    }
}

private fun <T> withFlyway(cmdConfig: FlywayCommandConfig, block: (Flyway) -> T) {
    configuredDataSource(cmdConfig.svcConfig).use { ds ->
        block(Flyway.configure().dataSource(ds).load())
    }
}

private class FlywayCommandConfig(val svcConfig: Configuration)

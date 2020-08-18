package org.mpierce.ktordemo

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EmptyConfiguration
import com.natpryce.konfig.Key
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.intType
import com.natpryce.konfig.stringType
import com.zaxxer.hikari.HikariConfig
import java.nio.file.Files
import java.nio.file.Path

/**
 * This wrapper class insulates the rest of the code from the specific choice of the Konfig library.
 *
 * If you choose to use a different confib lib later on, it will be easy to change.
 *
 * In addition, by looking up the config values when this object is constructed, you'll get any errors about missing
 * config values quickly.
 */
class HttpServerConfig(config: Configuration) {
    val httpPort: Int = config[Key("KTOR_DEMO_HTTP_PORT", intType)]
}

/**
 * Load a [HikariConfig] from a [Configuration].
 *
 * This assumes that the following properties are available (when prefixed with the supplied `prefix`):
 *
 * - `HOST`
 * - `PORT`
 * - `DATABASE`
 * - `DATA_SOURCE_CLASS` - the class name of the `DataSource` implementation, e.g. [`org.postgresql.ds.PGSimpleDataSource`](https://jdbc.postgresql.org/documentation/head/ds-ds.html)
 * - `USER`
 * - `PASSWORD`
 * - `MAX_POOL_SIZE` - see https://github.com/brettwooldridge/HikariCP#frequently-used. if unsure, 10 is a good default.
 * - `CONN_INIT_SQL` - SQL to run for each new connection created. A good place to set the timezone, e.g. `SET TIME ZONE 'UTC'` for Postgres.
 * - `AUTO_COMMIT` - should be false, but may have to be set to true for legacy apps that don't manage transactions properly.
 *
 * The provided `prefix` will be stitched together with the above property names, so prefix = "PRIMARY_DB_" would lead to the property `PRIMARY_DB_HOST` being used for the hostname to connect to.
 *
 * The returned `HikariConfig` can be further configured if need be (e.g. adding additional datasource properties); the
 * things set here are simply the ones that all applications should set at a minimum.
 */
fun buildHikariConfig(config: Configuration, prefix: String): HikariConfig {
    return HikariConfig().apply {
        // shared config for all drivers
        dataSourceClassName = config[Key("${prefix}DATA_SOURCE_CLASS", stringType)]
        username = config[Key("${prefix}USER", stringType)]
        password = config[Key("${prefix}PASSWORD", stringType)]
        maximumPoolSize = config[Key("${prefix}MAX_POOL_SIZE", intType)]
        connectionInitSql = config[Key("${prefix}CONN_INIT_SQL", stringType)]
        isAutoCommit = config[Key("${prefix}AUTO_COMMIT", booleanType)]

        // pg-specific config. If you're using another driver, you will probably need to set these in a different way,
        // perhaps by stitching together a JDBC url with just these things.
        addDataSourceProperty("serverName", config[Key("${prefix}HOST", stringType)])
        addDataSourceProperty("portNumber", config[Key("${prefix}PORT", stringType)])
        addDataSourceProperty("databaseName", config[Key("${prefix}DATABASE", stringType)])
    }
}

/**
 * Load properties files in the provided directory in lexically sorted order.
 *
 * Later files overwrite previous files. In other words, data in 02-bar.properties will take precedence over
 * 01-foo.properties.
 */
fun ConfigurationProperties.Companion.fromDirectory(configDir: Path): Configuration {
    return Files.newDirectoryStream(configDir)
        .filter { p -> p.fileName.toString().endsWith("properties") }
        .asSequence()
        .sorted()
        // TODO explicitly use UTF-8 once https://github.com/npryce/konfig/pull/46 lands
        .map { p -> fromFile(p.toFile()) }
        .fold(EmptyConfiguration as Configuration) { acc, config ->
            com.natpryce.konfig.Override(config, acc)
        }
}

package org.mpierce.ktordemo

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.Key
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.intType
import com.natpryce.konfig.stringType
import com.zaxxer.hikari.HikariConfig

/**
 * Using an interface for config makes it easier to construct instances off of test config if needed.
 */
interface HttpServerConfig {
    val httpPort: Int
}

class KonfigHttpServerConfig(config: Configuration) : HttpServerConfig {
    override val httpPort: Int = config[Key("KTOR_DEMO_HTTP_PORT", intType)]
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
 * The provided `prefix` will be stitched together with the above property names with a `_`, so prefix = "PRIMARY_DB" would lead to the property `PRIMARY_DB_HOST` being used for the hostname to connect to.
 *
 * The returned `HikariConfig` can be further configured if need be (e.g. adding additional datasource properties); the
 * things set here are simply the ones that all applications should set at a minimum.
 */
fun buildHikariConfig(config: Configuration, prefix: String): HikariConfig {
    return HikariConfig().apply {
        // shared config for all drivers
        dataSourceClassName = config[Key("${prefix}_DATA_SOURCE_CLASS", stringType)]
        username = config[Key("${prefix}_USER", stringType)]
        password = config[Key("${prefix}_PASSWORD", stringType)]
        maximumPoolSize = config[Key("${prefix}_MAX_POOL_SIZE", intType)]
        connectionInitSql = config[Key("${prefix}_CONN_INIT_SQL", stringType)]
        isAutoCommit = config[Key("${prefix}_AUTO_COMMIT", booleanType)]

        // pg-specific config. If you're using another driver, you will probably need to set these in a different way,
        // perhaps by stitching together a JDBC url with just these things.
        addDataSourceProperty("serverName", config[Key("${prefix}_HOST", stringType)])
        addDataSourceProperty("portNumber", config[Key("${prefix}_PORT", stringType)])
        addDataSourceProperty("databaseName", config[Key("${prefix}_DATABASE", stringType)])
    }
}

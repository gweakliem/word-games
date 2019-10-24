package org.mpierce.ktordemo

import org.apache.commons.configuration.Configuration

/**
 * Using an interface for config makes it easier to construct instances off of test config if needed
 */
interface KtorDemoConfig {
    fun dataSourceConfig(): DataSourceConfig

    fun httpPort(): Int
}

const val connInitSql = "SET TIME ZONE 'UTC'"
const val dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"

class CommonsConfigKtorDemoConfig(private val config: Configuration) : KtorDemoConfig {
    override fun dataSourceConfig(): DataSourceConfig {
        return DataSourceConfig(
                dataSourceClassName,
                config.getString("KTOR_DEMO_DB_USER"),
                config.getString("KTOR_DEMO_DB_PASSWORD"),
                4,
                1,
                connInitSql,
                mapOf("databaseName" to "ktor-demo-dev",
                        "portNumber" to config.getString("KTOR_DEMO_DB_PORT"),
                        "serverName" to config.getString("KTOR_DEMO_DB_IP"))
        )
    }

    override fun httpPort() = config.getInt("KTOR_DEMO_HTTP_PORT")

}

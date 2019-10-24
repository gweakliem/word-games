package org.mpierce.ktordemo

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Properties

class DataSourceConfig(
        val dataSourceClassName: String,
        val user: String,
        val password: String,
        val maxPoolSize: Int,
        val minPoolIdle: Int,
        val connInitSql: String,
        val dataSourceProperties: Map<String, String>)

/**
 * Build a HikariCP DataSource.
 *
 * The Hikari-specific type is returned rather than `javax.sql.DataSource` because `HikariDataSource implements `Closeable` but `DataSource` does not.
 */
fun buildDataSource(dsConfig: DataSourceConfig): HikariDataSource {
    val hikariConfig = HikariConfig()
    val properties = Properties().apply { putAll(dsConfig.dataSourceProperties) }

    hikariConfig.apply {
        dataSourceClassName = dsConfig.dataSourceClassName
        dataSourceProperties = properties
        username = dsConfig.user
        password = dsConfig.password
        isAutoCommit = false
        maximumPoolSize = dsConfig.maxPoolSize
        minimumIdle = dsConfig.minPoolIdle
        connectionInitSql = dsConfig.connInitSql
    }


    return HikariDataSource(hikariConfig)
}

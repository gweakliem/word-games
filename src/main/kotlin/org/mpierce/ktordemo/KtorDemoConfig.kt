package org.mpierce.ktordemo

import org.skife.config.Config
import org.skife.config.Default

interface KtorDemoConfig {
    @Config("KTOR_DEMO_DB_IP")
    @Default("127.0.0.1")
    fun dbIp(): String

    @Config("KTOR_DEMO_DB_PORT")
    @Default("25432")
    fun dbPort(): Int

    @Config("KTOR_DEMO_DB_USER")
    @Default("ktor-demo-dev")
    fun dbUser(): String

    @Config("KTOR_DEMO_DB_PASSWORD")
    @Default("ktor-demo-dev")
    fun dbPassword(): String

    @Config("KTOR_DEMO_HTTP_PORT")
    @Default("9080")
    fun httpPort(): Int
}

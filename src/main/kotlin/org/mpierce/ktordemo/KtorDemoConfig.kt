package org.mpierce.ktordemo

import org.skife.config.Config

interface KtorDemoConfig {
    @Config("KTOR_DEMO_DB_IP")
    fun dbIp(): String

    @Config("KTOR_DEMO_DB_PORT")
    fun dbPort(): Int

    @Config("KTOR_DEMO_DB_USER")
    fun dbUser(): String

    @Config("KTOR_DEMO_DB_PASSWORD")
    fun dbPassword(): String

    @Config("KTOR_DEMO_HTTP_PORT")
    fun httpPort(): Int
}

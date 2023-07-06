package org.mpierce.ktordemo

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.mpierce.ktordemo.cli.Flyway
import org.mpierce.ktordemo.cli.RunServer
import org.slf4j.bridge.SLF4JBridgeHandler
import java.time.Instant

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        // Use SLF4J for java.util.logging
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
        // so we can measure startup time
        val start = Instant.now()

        object : CliktCommand() {
            init {
                subcommands(
                    RunServer(start),
                    Flyway(),
                )
            }

            override fun run() {
                // no op
            }
        }.main(args)
    }
}

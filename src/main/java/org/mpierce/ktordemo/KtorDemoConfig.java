package org.mpierce.ktordemo;

import org.skife.config.Config;
import org.skife.config.Default;

// This is a Java class because it's easier to give config-magic the type it expects from plain Java.
public interface KtorDemoConfig {
    @Config("KTOR_DEMO_DB_IP")
    @Default("127.0.0.1")
    String dbIp();

    @Config("KTOR_DEMO_DB_PORT")
    @Default("25432")
    int dbPort();

    @Config("KTOR_DEMO_DB_USER")
    @Default("local-dev")
    String dbUser();

    @Config("KTOR_DEMO_DB_PASSWORD")
    @Default("local-dev")
    String dbPassword();

    @Config("KTOR_DEMO_HTTP_PORT")
    @Default("9080")
    int httpPort();
}

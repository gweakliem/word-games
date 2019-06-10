import org.flywaydb.gradle.task.FlywayCleanTask
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.postgresql.Driver
import java.util.Properties

buildscript {
    dependencies {
        classpath("org.postgresql:postgresql:42.2.5")
        // used by jooq gradle plugin to write config
        classpath("com.sun.xml.bind:jaxb-impl:2.3.0.1")
        classpath("com.sun.xml.bind:jaxb-core:2.3.0.1")
        classpath("com.sun.activation:javax.activation:1.2.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.31"
    id("application")
    id("org.flywaydb.flyway") version "5.2.4"
    id("nu.studer.jooq") version "3.0.3"
    id("com.github.ben-manes.versions") version "0.21.0"
}

val deps by extra {
    mapOf(
            "ktor" to "1.2.1",
            // also see version in buildscript
            "postgresql" to "42.2.5",
            "jackson" to "2.9.9",
            "slf4j" to "1.7.26",
            // also see versions in buildscript
            "jaxb" to "2.3.0.1",
            "jaxbApi" to "2.3.0",
            "activation" to "1.2.0",
            "junit" to "5.4.2"
    )
}

val dbUser by extra { "ktor-demo-dev" }
val dbPass by extra { "ktor-demo-dev" }
val testDbUser by extra { "ktor-demo-test" }
val testDbPass by extra { "ktor-demo-test" }
val testDbName by extra { "ktor-demo-test" }
val jdbcUrl by extra { "jdbc:postgresql://localhost:25432/ktor-demo-dev" }
val testJdbcUrl by extra { "jdbc:postgresql://localhost:25432/$testDbName" }

apply(from = "jooq.gradle")

application {
    mainClassName = "org.mpierce.ktordemo.KtorDemoKt"
}
// 'run' is a kotlin built-in function
tasks.named<JavaExec>("run") {
    args = listOf("local-dev-config")
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.ktor:ktor-server-core:${deps["ktor"]}")
    implementation("io.ktor:ktor-server-netty:${deps["ktor"]}")
    implementation("io.ktor:ktor-jackson:${deps["ktor"]}")
    testImplementation("io.ktor:ktor-server-test-host:${deps["ktor"]}")

    implementation("com.fasterxml.jackson.core:jackson-databind:${deps["jackson"]}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${deps["jackson"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${deps["jackson"]}")

    runtime("ch.qos.logback:logback-classic:1.2.3")
    runtime("org.slf4j:jcl-over-slf4j:${deps["slf4j"]}")
    implementation("org.slf4j:jul-to-slf4j:${deps["slf4j"]}")

    implementation("com.google.inject:guice:4.2.2")

    implementation("org.skife.config:config-magic:0.17")
    implementation("commons-configuration:commons-configuration:1.10")

    implementation("com.zaxxer:HikariCP:3.3.1")
    implementation("org.jooq:jooq")
    runtime("org.postgresql:postgresql:${deps["postgresql"]}")
    jooqRuntime("org.postgresql:postgresql:${deps["postgresql"]}")
    jooqRuntime("com.sun.xml.bind:jaxb-impl:${deps["jaxb"]}")
    jooqRuntime("com.sun.xml.bind:jaxb-core:${deps["jaxb"]}")
    jooqRuntime("javax.xml.bind:jaxb-api:${deps["jaxbApi"]}")
    jooqRuntime("com.sun.activation:javax.activation:${deps["activation"]}")

    implementation("org.mpierce.guice.warmup:guice-warmup:0.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${deps["junit"]}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${deps["junit"]}")
}

tasks.test {
    useJUnitPlatform()
}

configurations.all {
    // don't let commons logging creep into the classpath; use jcl-over-slf4j instead
    exclude("commons-logging", "commons-logging")
}

flyway {
    url = jdbcUrl
    user = dbUser
    password = dbPass
}

tasks {
    val makeTestDb by registering {
        doLast {
            val props = Properties()
            props["user"] = dbUser
            props["password"] = dbPass
            // classpath gets weird: DriverManager uses Groovy's classpath, not the gradle task classpath, so the normal way
            // won't find the driver.
            Driver().connect(jdbcUrl, props).use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(
                            "SELECT count(*) FROM pg_catalog.pg_database WHERE datname = '$testDbName'")
                    val rowCount = if (rs.next()) {
                        rs.getInt(1)
                    } else {
                        0
                    }

                    if (rowCount == 0) {
                        // Needs to be superuser to create extensions.
                        stmt.execute("CREATE ROLE \"$testDbUser\" WITH SUPERUSER LOGIN PASSWORD '$testDbPass'")
                        stmt.execute("CREATE DATABASE \"$testDbName\" OWNER '$testDbUser'")
                    }
                }
            }
        }
    }

    val flywayCleanTest by registering(FlywayCleanTask::class) {
        url = testJdbcUrl
        user = testDbUser
        password = testDbPass

        dependsOn(makeTestDb)
    }

    val flywayMigrateTest by registering(FlywayMigrateTask::class) {
        url = testJdbcUrl
        user = testDbUser
        password = testDbPass

        dependsOn(makeTestDb)
        mustRunAfter(flywayCleanTest)
    }

    flywayMigrate {
        mustRunAfter(flywayClean)
    }

    test {
        dependsOn(flywayMigrateTest)
    }

    clean {
        dependsOn(flywayCleanTest)
    }
}

// compile to java 8 bytecode, not java 6
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

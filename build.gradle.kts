import org.flywaydb.gradle.task.FlywayCleanTask
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        classpath("org.postgresql:postgresql:42.2.12")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("application")
    id("org.flywaydb.flyway") version "6.3.2"
    id("nu.studer.jooq") version "4.1"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("org.jmailen.kotlinter") version "2.3.2"
}

val deps by extra {
    mapOf(
            "ktor" to "1.3.2",
            // also see version in buildscript
            "postgresql" to "42.2.12",
            "jackson" to "2.10.3",
            "slf4j" to "1.7.30",
            "junit" to "5.6.1"
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
    mainClassName = "org.mpierce.ktordemo.KtorDemo"
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.ktor:ktor-server-netty:${deps["ktor"]}")
    implementation("io.ktor:ktor-jackson:${deps["ktor"]}")
    testImplementation("io.ktor:ktor-server-test-host:${deps["ktor"]}")

    implementation("com.fasterxml.jackson.core:jackson-databind:${deps["jackson"]}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${deps["jackson"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${deps["jackson"]}")

    runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
    runtimeOnly("org.slf4j:jcl-over-slf4j:${deps["slf4j"]}")
    implementation("org.slf4j:jul-to-slf4j:${deps["slf4j"]}")

    implementation("com.google.inject:guice:4.2.3")

    implementation("com.natpryce", "konfig", "1.6.10.0")

    implementation("com.zaxxer:HikariCP:3.4.2")
    implementation("org.jooq:jooq")
    runtimeOnly("org.postgresql:postgresql:${deps["postgresql"]}")
    jooqRuntime("org.postgresql:postgresql:${deps["postgresql"]}")

    implementation("org.mpierce.guice.warmup:guice-warmup:0.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${deps["junit"]}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${deps["junit"]}")
}

configurations.all {
    // don't let commons logging creep into the classpath; use jcl-over-slf4j instead
    exclude("commons-logging", "commons-logging")
}

flyway {
    url = jdbcUrl
    user = dbUser
    password = dbPass
    validateMigrationNaming = true
}

tasks {
    // 'run' is a kotlin built-in function
    (run) {
        args = listOf("local-dev-config")
    }

    val flywayCleanTest by registering(FlywayCleanTask::class) {
        url = testJdbcUrl
        user = testDbUser
        password = testDbPass
    }

    val flywayMigrateTest by registering(FlywayMigrateTask::class) {
        url = testJdbcUrl
        user = testDbUser
        password = testDbPass

        mustRunAfter(flywayCleanTest)
    }

    flywayMigrate {
        mustRunAfter(flywayClean)
    }

    test {
        useJUnitPlatform()
        dependsOn(flywayMigrateTest)
    }

    clean {
        dependsOn(flywayCleanTest)
    }

    // compile to java 8 bytecode, not java 6
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

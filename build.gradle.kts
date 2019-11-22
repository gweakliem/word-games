import org.flywaydb.gradle.task.FlywayCleanTask
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        classpath("org.postgresql:postgresql:42.2.8")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.60"
    id("application")
    id("org.flywaydb.flyway") version "6.0.7"
    id("nu.studer.jooq") version "3.0.3"
    id("com.github.ben-manes.versions") version "0.27.0"
}

val deps by extra {
    mapOf(
            "ktor" to "1.2.5",
            // also see version in buildscript
            "postgresql" to "42.2.8",
            "jackson" to "2.10.1",
            "slf4j" to "1.7.28",
            "junit" to "5.5.2"
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

    runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
    runtimeOnly("org.slf4j:jcl-over-slf4j:${deps["slf4j"]}")
    implementation("org.slf4j:jul-to-slf4j:${deps["slf4j"]}")

    implementation("com.google.inject:guice:4.2.2")

    implementation("commons-configuration:commons-configuration:1.10")

    implementation("com.zaxxer:HikariCP:3.4.1")
    implementation("org.jooq:jooq")
    runtimeOnly("org.postgresql:postgresql:${deps["postgresql"]}")
    jooqRuntime("org.postgresql:postgresql:${deps["postgresql"]}")

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

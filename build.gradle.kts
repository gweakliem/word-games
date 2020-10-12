import org.flywaydb.gradle.task.FlywayCleanTask
import org.flywaydb.gradle.task.FlywayMigrateTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        classpath("org.postgresql", "postgresql", "42.2.17")
    }
}

plugins {
    kotlin("jvm") version "1.4.10"
    application
    id("org.flywaydb.flyway") version "7.0.2"
    id("nu.studer.jooq") version "5.1.1"
    id("com.github.ben-manes.versions") version "0.33.0"
    id("org.jmailen.kotlinter") version "3.2.0"
}

val deps by extra {
    mapOf(
        "jackson" to "2.11.3",
        "junit" to "5.7.0",
        "ktor" to "1.4.1",
        // also see version in buildscript
        "postgresql" to "42.2.17",
        "slf4j" to "1.7.30"
    )
}

val dbUser by extra { "ktor-demo-dev" }
val dbPass by extra { "ktor-demo-dev" }
val testDbUser by extra { "ktor-demo-test" }
val testDbPass by extra { "ktor-demo-test" }
val testDbName by extra { "ktor-demo-test" }
val jdbcUrl by extra { "jdbc:postgresql://localhost:25432/ktor-demo-dev" }
val testJdbcUrl by extra { "jdbc:postgresql://localhost:25432/$testDbName" }

jooq {
    version.set("3.13.4")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        // source set name to put source in
        create("main") {
            jooqConfiguration.apply {
                // muffle jooq's verbose output when generating code
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = jdbcUrl
                    user = dbUser
                    password = dbPass
                }
                generator.apply {
                    target.apply {
                        packageName = "org.mpierce.ktordemo.jooq"
                    }
                    database.apply {
                        inputSchema = "public"
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        // avoid needing another dependency just to have this annotation
                        isGeneratedAnnotation = false
                    }
                }
            }
        }
    }
}

application {
    mainClassName = "org.mpierce.ktordemo.KtorDemo"
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor", "ktor-server-netty", "${deps["ktor"]}")
    implementation("io.ktor", "ktor-jackson", "${deps["ktor"]}")
    testImplementation("io.ktor", "ktor-server-test-host", "${deps["ktor"]}")

    implementation("com.fasterxml.jackson.core", "jackson-databind", "${deps["jackson"]}")
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "${deps["jackson"]}")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "${deps["jackson"]}")
    // jackson-module-kotlin may at times be pulling in an old version of kotlin-reflect, so specify it manually
    // to make sure the version matches the kotlin stdlib
    implementation(kotlin("reflect"))

    runtimeOnly("ch.qos.logback", "logback-classic", "1.2.3")
    runtimeOnly("org.slf4j", "jcl-over-slf4j", "${deps["slf4j"]}")
    implementation("org.slf4j", "jul-to-slf4j", "${deps["slf4j"]}")

    implementation("com.google.inject", "guice", "4.2.3")

    implementation("com.natpryce", "konfig", "1.6.10.0")

    implementation("com.zaxxer", "HikariCP", "3.4.5")
    implementation("org.jooq", "jooq")
    runtimeOnly("org.postgresql", "postgresql", "${deps["postgresql"]}")
    jooqGenerator("org.postgresql", "postgresql", "${deps["postgresql"]}")
    jooqGenerator("org.slf4j", "slf4j-simple", deps["slf4j"])

    implementation("org.mpierce.guice.warmup", "guice-warmup", "0.1")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", "${deps["junit"]}")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "${deps["junit"]}")
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

    named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
        dependsOn(flywayMigrate)
        allInputsDeclared.set(true)
    }

    // compile to java 8 bytecode, not java 6
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

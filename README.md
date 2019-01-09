# What is this?

It's a small demo of using Ktor together with some other libraries to perform the sort of real world tasks common services perform: JSON serialization, working with a database, testing, etc. Feel free to use this project as a starting point; that's the idea! Nothing here is the "only way of doing things", so ditch anything that isn't a good fit for your project.

Its contrived, simplistic workload is to let you create "widgets" and then list them or request individual widgets.

I make no claim of using any particular feature in the most idiomatic way. If you have suggestions, file an issue or PR.

## Ingredients

- [Kotlin](https://kotlinlang.org/): A nice language for the JVM with coroutine support.
- [Gradle](https://gradle.org/): A pretty good mainstream JVM build tool.
- [Ktor](https://ktor.io/): Kotlin coroutine-focused library for writing services with nonblocking I/O.
- [Jackson](https://github.com/FasterXML/jackson): JSON serialization/deserialization, including showing how to configure the `ObjectMapper` because you always have to do that.
- [PostgreSQL](https://www.postgresql.org/): A good RDBMS -- most projects will be just fine with SQL.
- [Flyway](https://flywaydb.org/): DB migrations in plain SQL.
- [jOOQ](https://www.jooq.org/): Type-safe SQL generated from your DB structure.
- [HikariCP](https://github.com/brettwooldridge/HikariCP): A fast connection pool.
- [Config-magic](https://github.com/brianm/config-magic/): There are many ways of mapping config data into type safe, accessible language constructs. This is one of them.
- [Docker Compose](https://docs.docker.com/compose/): For easy local dev setup of Postgres.
- [Docker](https://docs.docker.com/reference/): Because everyone wants Docker images, even though you may [wish to use caution](https://thehftguy.com/2016/11/01/docker-in-production-an-history-of-failure/).

## Usage 

Create a widget using [httpie](https://httpie.org/) or your HTTP client of choice:

```
http POST 127.0.0.1:9080/widgets name=foo
```

List all the widgets widgets:

```
http GET 127.0.0.1:9080/widgets/all
```

Get a single widget:

```
http GET 127.0.0.1:9080/widgets/id/1
```

# Local dev 

Local dev setup steps:

- Install Java 11. [sdkman](http://sdkman.io/) is a handy tool for managing multiple JVM installations.
- Start a db container: `docker-compose up -d`
- Do a build: `./gradlew build`
    - This does a DB migration, which you can do yourself too: `./gradlew flywayMigrate`
    - It then generates jOOQ sources from that DB: `./gradlew generateKtorDemoJooqSchemaSource`
- Run `KtorDemo` via IntelliJ, or with `./gradlew run`.

# Testing

Run `./gradlew check`. (You'll need the local postgresql container running.)

Some things to note about the tests:

- Tests should run quickly. To help achieve that, database access is done via a `WidgetDao` interface which has 2 implementations: one that uses PostgreSQL, and another that's in-memory. The former is used only when it's the actual system under test, and the much cheaper in-memory one is used everywhere else. See `WedgetDaoTest.kt`.
- A separate database for tests is created in the postgresql container so that test data is isolated from any data you might have created when running the actual service. See `build.gradle`.
- See `WidgetEndpointTests` for how to test an endpoint. (Uses the aforementioned in-memory persistence implementation)

# Packaging into a Docker image

- Generate artifact: `./gradlew distTar`. This creates a tarball with all the dependencies and a handy script to run the thing.
- `docker build .` This puts the above tarball into a Docker image with a JVM.

To run the image, set environment variables (see `KtorDemoConfig`) to configure DB access.

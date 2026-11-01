// Standalone JVM test build. Compiles the pure-Java extension code and its unit tests
// without the Android SDK or the Morphe Gradle plugin (used where those are unavailable).
// Run: gradle -p tools/jvm-tests test
plugins {
    java
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

repositories {
    mavenCentral()
}

// Sources that need Android (android.*) and cannot compile on the plain JVM.
// Keep this list explicit and short; add files here when a class starts using android.*.
val androidOnly = listOf(
    "**/ui/**",
)

sourceSets {
    main {
        java {
            srcDir("../../extensions/extension/src/main/java")
            exclude(androidOnly)
        }
    }
    test {
        java.srcDir("../../extensions/extension/src/test/java")
        resources.srcDir("../../extensions/extension/src/test/resources")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = false }
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.allopen") version "1.9.20"
    id("io.quarkus") version "3.6.0"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.20"
    id("pmd")
    id("com.github.spotbugs") version "6.0.7"
    id("checkstyle")
}

group = "com.abservice"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    // Quarkus BOM
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))

    // Quarkus Core
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-resteasy-reactive")
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")

    // Database
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")

    // Blaze-Persistence
    implementation("com.blazebit:blaze-persistence-integration-quarkus:1.6.9")

    // Security
    implementation("io.quarkus:quarkus-oidc")
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-smallrye-jwt")

    // OpenAPI
    implementation("io.quarkus:quarkus-openapi-generator")
    implementation("io.quarkus:quarkus-swagger-ui")

    // Validation
    implementation("io.quarkus:quarkus-hibernate-validator")

    // Configuration
    implementation("io.quarkus:quarkus-config-yaml")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-kotlin")

    // Test
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-test-h2")
    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "25"
        javaParameters = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

quarkus {
    finalName = "abservice-backend"
    setOutputDirectory("build/quarkus-app")
}

// PMD configuration
pmd {
    toolVersion = "7.0.0"
    ruleSetFiles = files("config/pmd/pmd.xml")
    ruleSets = emptyList()
    ignoreFailures = false
}

// SpotBugs configuration
spotbugs {
    toolVersion.set("4.8.3")
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    excludeFilter.set(file("config/spotbugs/spotbugs.xml"))
    ignoreFailures.set(false)
}

spotbugsMain {
    reports {
        html {
            required.set(true)
            outputLocation.set(file("$buildDir/reports/spotbugs/main/spotbugs.html"))
        }
        xml {
            required.set(true)
            outputLocation.set(file("$buildDir/reports/spotbugs/main/spotbugs.xml"))
        }
    }
}

spotbugsTest {
    reports {
        html {
            required.set(true)
            outputLocation.set(file("$buildDir/reports/spotbugs/test/spotbugs.html"))
        }
        xml {
            required.set(true)
            outputLocation.set(file("$buildDir/reports/spotbugs/test/spotbugs.xml"))
        }
    }
}

// Checkstyle configuration
checkstyle {
    toolVersion = "10.12.4"
    configFile = file("config/checkstyle/checkstyle.xml")
    ignoreFailures = false
    maxWarnings = 0
}

checkstyleMain {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

checkstyleTest {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Add static analysis to build process
tasks.named("check") {
    dependsOn("pmdMain", "pmdTest", "spotbugsMain", "spotbugsTest", "checkstyleMain", "checkstyleTest")
}

// Create a task to run all static analysis
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Run all static analysis tools"
    dependsOn("pmdMain", "pmdTest", "spotbugsMain", "spotbugsTest", "checkstyleMain", "checkstyleTest")
}

// Gradle wrapper
tasks.wrapper {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.ALL
}

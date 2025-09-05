import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "com.memexos.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.1.4")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    compileOnly("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.5")
    compileOnly("org.jlleitschuh.gradle:ktlint-gradle:11.6.1")
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidTestConvention") {
            id = "memexos.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("staticAnalysisConvention") {
            id = "memexos.static.analysis"
            implementationClass = "StaticAnalysisConventionPlugin"
        }
        register("performanceTestConvention") {
            id = "memexos.performance.test"
            implementationClass = "PerformanceTestConventionPlugin"
        }
    }
}

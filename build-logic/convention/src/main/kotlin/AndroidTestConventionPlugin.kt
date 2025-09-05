import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configure<BaseExtension> {
                // Configure test options
                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                        isReturnDefaultValues = true
                    }
                    animationsDisabled = true
                }

                // Configure test orchestrator
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    testInstrumentationRunnerArguments["clearPackageData"] = "true"
                }

                // Configure test source sets
                sourceSets {
                    getByName("test") {
                        java.srcDir("src/testShared/java")
                        resources.srcDir("src/testShared/resources")
                    }
                    getByName("androidTest") {
                        java.srcDir("src/testShared/java")
                        resources.srcDir("src/testShared/resources")
                    }
                }

                // Configure packaging options for tests
                packagingOptions {
                    resources.excludes.addAll(
                        listOf(
                            "META-INF/LICENSE*",
                            "META-INF/AL2.0",
                            "META-INF/LGPL2.1",
                            "**/attach_hotspot_windows.dll",
                            "META-INF/licenses/**",
                            "META-INF/DEPENDENCIES"
                        )
                    )
                }
            }

            // Configure JUnit 5 for unit tests
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    events("passed", "skipped", "failed")
                    showStandardStreams = false
                }
                
                // Configure JVM options for tests
                jvmArgs(
                    "-XX:+UseG1GC",
                    "-Xmx2g",
                    "-Djunit.jupiter.execution.parallel.enabled=true",
                    "-Djunit.jupiter.execution.parallel.mode.default=concurrent",
                    "-Djunit.jupiter.execution.parallel.config.strategy=dynamic"
                )
            }

            // Add test dependencies
            dependencies {
                // Unit Testing
                "testImplementation"("junit:junit:4.13.2")
                "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.1")
                "testImplementation"("org.mockito:mockito-core:5.5.0")
                "testImplementation"("org.mockito.kotlin:mockito-kotlin:5.1.0")
                "testImplementation"("io.mockk:mockk:1.13.8")
                "testImplementation"("com.google.truth:truth:1.1.4")
                "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                "testImplementation"("org.robolectric:robolectric:4.11.1")
                "testImplementation"("app.cash.turbine:turbine:1.0.0")
                "testImplementation"("com.squareup.okhttp3:mockwebserver:4.12.0")
                
                // Test engines
                "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.10.1")
                "testRuntimeOnly"("org.junit.vintage:junit-vintage-engine:5.10.1")

                // Android Testing
                "androidTestImplementation"("androidx.test:core:1.5.0")
                "androidTestImplementation"("androidx.test.ext:junit:1.1.5")
                "androidTestImplementation"("androidx.test:runner:1.5.2")
                "androidTestImplementation"("androidx.test:rules:1.5.0")
                "androidTestImplementation"("androidx.test.espresso:espresso-core:3.5.1")
                "androidTestImplementation"("androidx.test.espresso:espresso-web:3.5.1")
                "androidTestImplementation"("androidx.test.espresso:espresso-contrib:3.5.1")
                "androidTestImplementation"("androidx.test.espresso:espresso-intents:3.5.1")
                "androidTestImplementation"("androidx.test.uiautomator:uiautomator:2.2.0")
                "androidTestImplementation"("com.google.truth:truth:1.1.4")
                "androidTestImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                
                // Test orchestrator
                "androidTestUtil"("androidx.test:orchestrator:1.4.2")
            }
        }
    }
}

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class PerformanceTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configure<BaseExtension> {
                // Configure baseline profiles
                defaultConfig {
                    if (this is com.android.build.gradle.internal.dsl.DefaultConfig) {
                        // Enable baseline profiles
                        manifestPlaceholders["profileable"] = true
                    }
                }

                // Configure build types for benchmarking
                buildTypes {
                    create("benchmark") {
                        isDebuggable = false
                        isMinifyEnabled = true
                        isShrinkResources = false
                        signingConfig = getByName("debug").signingConfig
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                            "benchmark-rules.pro"
                        )
                    }
                }
            }

            // Add performance testing dependencies
            dependencies {
                // Benchmark dependencies
                "androidTestImplementation"("androidx.benchmark:benchmark-macro-junit4:1.2.1")
                "androidTestImplementation"("androidx.test.uiautomator:uiautomator:2.2.0")
                "androidTestImplementation"("androidx.test.ext:junit:1.1.5")
                
                // Profile installer for baseline profiles
                "implementation"("androidx.profileinstaller:profileinstaller:1.3.1")
            }

            // Create benchmark tasks
            tasks.register("generateBaselineProfile") {
                description = "Generate baseline profile for improved startup performance"
                group = "benchmark"
                
                doLast {
                    println("Run macrobenchmarks to generate baseline profile")
                    println("Profile will be generated in app/src/main/baseline-prof.txt")
                }
            }

            tasks.register("benchmarkAll") {
                description = "Run all benchmark tests"
                group = "benchmark"
                
                dependsOn("connectedBenchmarkAndroidTest")
            }
        }
    }
}

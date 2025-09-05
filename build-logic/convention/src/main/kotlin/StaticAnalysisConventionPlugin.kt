import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

class StaticAnalysisConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply plugins
            apply<DetektPlugin>()
            apply<KtlintPlugin>()

            // Configure Detekt
            configure<DetektExtension> {
                config.setFrom("$rootDir/config/detekt/detekt.yml")
                buildUponDefaultConfig = true
                autoCorrect = false
                parallel = true
                ignoreFailures = false
                basePath = rootDir.absolutePath
                
                reports {
                    html {
                        required.set(true)
                        outputLocation.set(file("build/reports/detekt/detekt.html"))
                    }
                    xml {
                        required.set(true)
                        outputLocation.set(file("build/reports/detekt/detekt.xml"))
                    }
                    sarif {
                        required.set(true)
                        outputLocation.set(file("build/reports/detekt/detekt.sarif"))
                    }
                }
            }

            // Configure KtLint
            configure<KtlintExtension> {
                version.set("0.50.0")
                android.set(true)
                ignoreFailures.set(false)
                reporters {
                    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
                    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
                    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
                }
                filter {
                    exclude("**/generated/**")
                    exclude("**/build/**")
                    include("**/kotlin/**")
                    include("**/java/**")
                }
            }

            // Configure Detekt tasks
            tasks.withType<Detekt>().configureEach {
                jvmTarget = "1.8"
                reports {
                    html.required.set(true)
                    xml.required.set(true)
                    sarif.required.set(true)
                }
                exclude("**/build/**", "**/resources/**")
                include("**/*.kt", "**/*.kts")
            }

            // Add Detekt dependencies
            dependencies {
                "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.4")
                "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.4")
            }

            // Create quality check task
            tasks.register("qualityCheck") {
                description = "Run all quality checks (detekt, ktlint, lint)"
                group = "verification"
                
                dependsOn("ktlintCheck", "detekt", "lint")
            }
        }
    }
}

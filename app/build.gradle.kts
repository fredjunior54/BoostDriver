plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}
android {
    namespace = "com.boostdriver"
    compileSdk = 36
    buildFeatures { compose = true }
    defaultConfig { applicationId = "com.boostdriver"; minSdk = 23; targetSdk = 36; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}

detekt {
    source.setFrom("src/main/java", "src/test/java")
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
}

val detektDesign = tasks.register("detektDesign") {
    group = "verification"
    val ui = fileTree("src/main/java") { include("**/ui/**/*.kt"); exclude("**/theme/**") }
    inputs.files(ui)
    doLast {
        val rawTokens = Regex("""\b[1-9]\d*(?:\.\d+)?\.(?:dp|sp)\b|\bColor\(\s*0x|\bRoundedCornerShape\(""")
        val violations = ui.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (rawTokens.containsMatchIn(line)) "${file.relativeTo(projectDir)}:${index + 1}" else null
            }
        }
        check(violations.isEmpty()) { "Use theme tokens at:\n${violations.joinToString("\n")}" }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "17" }

tasks.named("check") { dependsOn(detektDesign) }
tasks.named("detekt") { dependsOn(detektDesign) }

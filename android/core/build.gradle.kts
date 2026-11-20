import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin business rules: no Android dependency, so tests run on the plain JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // The shared fixtures live at the repository root, next to ios/ and android/.
    systemProperty("tamdid.repoRoot", rootProject.projectDir.parentFile.absolutePath)
}

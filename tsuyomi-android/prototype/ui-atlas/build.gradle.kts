import java.security.MessageDigest

fun ByteArray.hexString(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

fun sha256File(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).hexString()

val prototypeDataSchemaVersion = 1
val prototypeReviewSchemaVersion = 2
val designRulesFile = rootProject.file("docs/design/UI_CONSTITUTION.md")
val designRulesSha256 = sha256File(designRulesFile)
val prototypeBuildId = MessageDigest.getInstance("SHA-256").run {
    fileTree(projectDir) {
        exclude(
            "build/**",
            ".gradle/**",
            "src/screenshotTestDebug/**",
            "tools/**",
            "**/__pycache__/**",
            "**/*.pyc",
            "**/tsuyomi-atlas-review-bundle*.json",
            "local.properties",
            "render-browser-atlas.bat",
        )
    }.files.sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }.forEach { file ->
        update(file.relativeTo(projectDir).invariantSeparatorsPath.toByteArray())
        update(byteArrayOf(0))
        update(file.readBytes())
        update(byteArrayOf(0))
    }
    update(designRulesFile.readBytes())
    update(prototypeDataSchemaVersion.toString().toByteArray())
    update(prototypeReviewSchemaVersion.toString().toByteArray())
    digest().hexString()
}

// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.screenshot)
    id("tsuyomi.android.application")
    id("tsuyomi.android.compose")
}

android {
    namespace = "org.tsuyomi.prototype.uiatlas"
    defaultConfig {
        applicationId = "org.tsuyomi.prototype.uiatlas"
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PROTOTYPE_BUILD_ID", "\"$prototypeBuildId\"")
        buildConfigField("String", "DESIGN_RULES_SHA256", "\"$designRulesSha256\"")
        buildConfigField("int", "PROTOTYPE_DATA_SCHEMA_VERSION", prototypeDataSchemaVersion.toString())
        buildConfigField("int", "PROTOTYPE_REVIEW_SCHEMA_VERSION", prototypeReviewSchemaVersion.toString())
    }
    buildFeatures {
        buildConfig = true
    }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}

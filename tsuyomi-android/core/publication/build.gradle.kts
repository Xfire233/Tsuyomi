// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
    id("tsuyomi.android.library")
}
abstract class GenerateMobiParser : Exec() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val generatedParserAssets = layout.buildDirectory.dir("generated/assets/mobi")
val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "npm") else listOf("npm")
val installParserBuildDependencies = tasks.register<Exec>("installParserBuildDependencies") {
    workingDir("parser-js")
    commandLine(npmCommand + "ci")
    inputs.files("parser-js/package.json", "parser-js/package-lock.json")
    outputs.dir("parser-js/node_modules")
}
val generateMobiParser = tasks.register<GenerateMobiParser>("generateMobiParser") {
    dependsOn(installParserBuildDependencies)
    outputDirectory.set(generatedParserAssets)
    workingDir("parser-js")
    commandLine(npmCommand + listOf("run", "build"))
    environment("TSUYOMI_MOBI_OUTPUT", generatedParserAssets.get().file("publication/mobi-quickjs.mjs").asFile.absolutePath)
    inputs.files("parser-js/build.mjs", "parser-js/source-pins.json", "parser-js/THIRD_PARTY_NOTICES.txt")
    inputs.dir("parser-js/src")
    inputs.dir("parser-js/vendor")
}

android {
    namespace = "org.tsuyomi.core.publication"
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateMobiParser) { it.outputDirectory }
}

dependencies {
    api(project(":shared:library-domain"))
    api(project(":shared:model"))
    api(project(":shared:locator"))
    api(project(":shared:source-contract"))
    implementation(project(":shared:backup"))
    implementation(project(":core:files"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

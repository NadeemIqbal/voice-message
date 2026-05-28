import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.maven.publish)
}

// Companion artifact for voice-message: opt-in audio adapters with platform actuals so
// consumers can skip wiring `MediaRecorder` / `AVAudioRecorder` / `JavaSound` / Web `MediaRecorder`
// themselves. Released in lockstep with the main library starting at v0.3.0.
val libVersion: String =
    (System.getenv("RELEASE_VERSION") ?: findProperty("version") as String?)
        ?.removePrefix("v")
        ?.takeUnless { it.isBlank() || it == "unspecified" }
        ?: "0.3.0"

group = "io.github.nadeemiqbal"
version = libVersion

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            compileTaskProvider.configure { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
        }
    }

    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // The main library: this artifact extends rememberVoiceRecorderState with bound
            // audio capture, so it has a hard dependency on the main lib.
            api(project(":voice-message"))
            implementation(compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidMain by getting {
            dependencies {
                // LocalContext lives in compose.ui; the Android actual reads it to scope the
                // MediaRecorder's temp-file to the host's cacheDir.
                implementation(compose.ui)
            }
        }
    }
}

android {
    namespace = "io.github.nadeemiqbal.voicemessage.audio"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Wasm test infra cannot load Skiko under Node and the browser runner needs a local Chrome.
// Pure-logic tests run on Desktop + iOS targets; Wasm is compile-only.
tasks.matching { it.name == "wasmJsBrowserTest" || it.name == "wasmJsNodeTest" }
    .configureEach { enabled = false }

mavenPublishing {
    publishToMavenCentral()

    if (
        project.hasProperty("signingInMemoryKey") ||
        project.hasProperty("signing.keyId")
    ) {
        signAllPublications()
    }

    coordinates("io.github.nadeemiqbal", "voice-message-audio", libVersion)

    pom {
        name.set("VoiceMessage Audio")
        description.set(
            "Drop-in audio capture for voice-message. Implements VoiceAudioCapture on every CMP " +
                "target: MediaRecorder on Android, AVAudioRecorder on iOS, javax.sound.sampled " +
                "on Desktop, MediaRecorder + AnalyserNode on Web. Pair with rememberVoiceRecorderState " +
                "for a fully working hold-to-record voice messaging UI without BYO audio plumbing.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/NadeemIqbal/voice-message")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("NadeemIqbal")
                name.set("Nadeem Iqbal")
                email.set("mr_nadeem_iqbal@yahoo.com")
                url.set("https://github.com/NadeemIqbal")
            }
        }
        scm {
            url.set("https://github.com/NadeemIqbal/voice-message")
            connection.set("scm:git:git://github.com/NadeemIqbal/voice-message.git")
            developerConnection.set("scm:git:ssh://git@github.com/NadeemIqbal/voice-message.git")
        }
    }
}

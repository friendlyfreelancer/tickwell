import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Same untracked keystore.properties as the other modules (see README).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.friendlyfreelancer.classicfaces"
    compileSdk = 36

    defaultConfig {
        // One Play listing: this watch app and the phone app share the package.
        applicationId = "com.friendlyfreelancer.classicfaces"
        // Watch Face Push needs Wear OS 6.
        minSdk = 36
        targetSdk = 36
        // Distinct from the phone app's version code, as Play requires.
        versionCode = 1000 + (property("appVersionCode") as String).toInt()
        versionName = property("appVersionName") as String
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Java 17 bytecode from whatever JDK runs Gradle (Android Studio bundles 21).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * Validates the release watch face with Google's Watch Face Push validator and
 * bundles it as assets/default_watchface.apk, with its validation token as the
 * string resource the manifest points at.
 */
abstract class BundleWatchFace @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {
    @get:InputFile abstract val faceApk: RegularFileProperty
    @get:InputFile abstract val validatorJar: RegularFileProperty
    @get:Input abstract val marketplacePackage: Property<String>
    @get:OutputDirectory abstract val assetsDir: DirectoryProperty
    @get:OutputDirectory abstract val resDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val output = ByteArrayOutputStream()
        exec.exec {
            commandLine(
                File(System.getProperty("java.home"), "bin/java").path,
                "-jar", validatorJar.get().asFile.path,
                "--apk_path=${faceApk.get().asFile.path}",
                "--package_name=${marketplacePackage.get()}",
            )
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        val log = output.toString()
        val token = Regex("generated token: (\\S+)").find(log)?.groupValues?.get(1)
            ?: throw GradleException("Watch face failed validation:\n$log")

        faceApk.get().asFile.copyTo(assetsDir.file("default_watchface.apk").get().asFile, true)
        val values = resDir.dir("values").get().asFile.apply { mkdirs() }
        File(values, "watchface_token.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="default_wf_token" translatable="false">$token</string>
            |</resources>
            |""".trimMargin()
        )
    }
}

val bundleWatchFace = tasks.register<BundleWatchFace>("bundleWatchFace") {
    dependsOn(":face:assembleRelease")
    faceApk.set(project(":face").layout.buildDirectory.file("outputs/apk/release/face-release.apk"))
    validatorJar.set(rootProject.file("tools/validator-push-cli.jar"))
    marketplacePackage.set(android.defaultConfig.applicationId)
    assetsDir.set(layout.buildDirectory.dir("generated/watchface/assets"))
    resDir.set(layout.buildDirectory.dir("generated/watchface/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleWatchFace, BundleWatchFace::assetsDir)
        variant.sources.res?.addGeneratedSourceDirectory(bundleWatchFace, BundleWatchFace::resDir)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.wear.compose:compose-material:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("androidx.wear.watchfacepush:watchfacepush:1.0.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

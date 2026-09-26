import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing comes from an untracked keystore.properties (see README);
// without one, release builds fall back to the debug key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.friendlyfreelancer.classicfaces.watchface"
    compileSdk = 36

    defaultConfig {
        // Installed by the wear app through Watch Face Push, which requires
        // "<marketplace package>.watchfacepush.<name>".
        applicationId = "com.friendlyfreelancer.classicfaces.watchfacepush.classic"
        // Watch Face Format v1 runs on Wear OS 4 (API 33) and newer.
        minSdk = 33
        targetSdk = 36
        versionCode = (property("appVersionCode") as String).toInt()
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
            // Strips the generated R class so the bundle ships no dex, which
            // Play requires for Watch Face Format packages.
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
}

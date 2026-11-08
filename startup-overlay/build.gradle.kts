plugins { id("com.android.application") }

android {
    namespace = "local.cleanhome.startup"
    compileSdk = 34
    defaultConfig {
        applicationId = "local.cleanhome.startup"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("personal") {
            val folder = file(System.getenv("CLEAN_HOME_SIGNING_DIR") ?: "${System.getProperty("user.home")}/.local/share/yasuda-clean-home/signing")
            storeFile = file("$folder/release.jks")
            storePassword = file("$folder/store.password").readText().trim()
            keyAlias = "cleanhome"
            keyPassword = storePassword
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("personal") } }
    lint { abortOnError = true }
}

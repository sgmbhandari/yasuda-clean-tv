plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "local.cleanhome.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "local.cleanhome.tv"
        minSdk = 28
        targetSdk = 34
        versionCode = 7
        versionName = "1.4.0"
        testInstrumentationRunner = "local.cleanhome.tv.CleanHomeTestRunner"
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
    buildTypes {
        getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("personal") }
        getByName("debug") { signingConfig = signingConfigs.getByName("personal") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    val sdk = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
    androidTestCompileOnly(files("$sdk/platforms/android-34/optional/android.test.runner.jar", "$sdk/platforms/android-34/optional/android.test.base.jar", "$sdk/platforms/android-34/optional/android.test.mock.jar"))
}

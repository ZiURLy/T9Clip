plugins { id("com.android.application") }

android {
    namespace = "com.ziur.t9clip"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ziur.t9clip"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.alauncher.app.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}

baselineProfile {
    // This is where you specify how many iterations should be run to collect the profile.
    // More iterations lead to more accurate profiles but take longer.
    filter {
        include("com.alauncher.app.**")
    }
}

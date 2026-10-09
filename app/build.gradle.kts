plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// CI передаёт номер запуска: каждая сборка из GitHub получает versionCode больше предыдущей.
// Локальные сборки имеют код 1 — автообновление для них выключено.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

// Репозиторий GitHub, в котором CI публикует релизы build-N (приложение проверяет их для автообновления).
// Не менять у выпущенного приложения: старые версии смотрят именно сюда.
val updateRepo = "dstpnklen20-hue/What-to-wear"

android {
    namespace = "ru.whattowear.app"
    compileSdk = 37

    defaultConfig {
        // Никогда не менять: обновление ставится поверх только с тем же id и тем же ключом подписи.
        applicationId = "ru.whattowear.app"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI подписывает постоянным ключом из GitHub Secrets (в репозитории его нет), чтобы новые сборки
    // вставали поверх установленных. Без переменных окружения используется debug-ключ.
    val keystoreFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("ci") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("ci") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

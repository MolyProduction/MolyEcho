import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    id("dev.sergiobelda.compose.vectorize") version "1.0.2"
    id("com.squareup.sqldelight")
    //alias(libs.plugins.app.icon)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.compose.ui.preview)
            implementation(libs.androidx.compose.ui.tooling)
            implementation(libs.androidx.compose.ui.util)
            implementation(libs.google.accompanist.systemuicontroller)
            implementation(libs.sqldelight.android.driver)

            implementation(libs.google.accompanist.systemuicontroller)

            implementation(libs.kotlinx.serialization.json)
            implementation(project(":lib"))

            // splash
            implementation(libs.core.splashscreen)
            implementation(libs.androidx.compose.documentfile)
        }

        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(libs.material.icons.core)
            implementation(compose.components.resources)

            implementation(compose.components.resources)
            implementation(libs.compose.vectorize.core)
            implementation(libs.kotlinx.serialization.json)

            // koin
            implementation(libs.koin.core)
            implementation(libs.koin.test)
            implementation(libs.koin.compose.viewmodel)


            // navigation
            implementation(libs.navigation.compose)

            // logging
            implementation(libs.napier)

            // Data store
            implementation(libs.datastore.preferences)
            implementation(libs.datastore)

            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(project(":core:audio"))
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("junit:junit:4.13.2")
            }
        }
    }

    targets.all {
        compilations.all {
            compilerOptions.configure {
                allWarningsAsErrors = false
                freeCompilerArgs.add("-Xexpected-actual-classes")
                // For deterministic builds
                freeCompilerArgs.add("-Xjsr305=strict")
                freeCompilerArgs.add("-Xno-param-assertions")
                freeCompilerArgs.add("-Xno-call-assertions")
                freeCompilerArgs.add("-Xno-receiver-assertions")
                freeCompilerArgs.add("-Xno-optimize")
                freeCompilerArgs.add("-Xassertions=jvm")
                freeCompilerArgs.add("-Xuse-deterministic-jar-order")
                freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            }
        }
    }

}

compose.resources {
    publicResClass = true
    packageOfResClass = "de.molyecho.notlyvoice.resources"
    generateResClass = always
}

sqldelight {
    database("NoteDatabase") {
        packageName = "com.module.notelycompose.database"
        sourceFolders = listOf("sqldelight")
    }
}
android {
    namespace = "de.molyecho.notlyvoice.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].assets.srcDirs("src/androidMain/assets")
    // Removed src/commonMain/resources
    // sourceSets["main"].resources.srcDirs("src/commonMain/resources")
    defaultConfig {
        applicationId = "de.molyecho.notlyvoice.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 27
        versionName = "1.2.6"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // Ensure reproducible packaging
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.version"
            excludes += "assets/composeResources/de.molyecho.notlyvoice.resources/strings.xml"
        }

        // Force deterministic file ordering
        jniLibs {
            useLegacyPackaging = true
            // 16KB Page Size Support: Use uncompressed native libraries
            pickFirsts += listOf("**/libc++_shared.so", "**/libwhisper.so", "**/libonnxruntime.so")
        }

        // Ensure reproducible DEX files
        dex {
            useLegacyPackaging = false
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // uncomment to run on release for testing
            // signingConfig = signingConfigs.getByName("debug")
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    ndkVersion = "27.0.12077973"
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.animation.android)
    implementation(libs.androidx.appcompat)
}

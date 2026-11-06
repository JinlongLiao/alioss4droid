import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 从 keystore.properties 读取发布签名配置；文件不存在时跳过 release 签名（仍能出包但未签名）。
// 文件格式请见 keystore.properties.sample。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true
val hasDebugKeystore = keystoreProps.getProperty("debugStoreFile")?.let { rootProject.file(it).exists() } == true

// Robolectric 的 Maven resolver 不展开 settings.xml 中的用户目录变量，仅传入同一配置的实际路径。
val mavenUserHome = System.getProperty("user.home")
val mavenSettingsFile = File(mavenUserHome, ".m2/settings.xml")
val configuredMavenRepository = if (mavenSettingsFile.exists()) {
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(mavenSettingsFile)
        .getElementsByTagName("localRepository").item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
} else null
val testMavenRepository = configuredMavenRepository
    ?.replace("${'$'}{user.home}", mavenUserHome)
    ?: File(mavenUserHome, ".m2/repository").absolutePath

android {
    namespace = "io.github.jinlongliao.ossbrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jinlongliao.ossbrowser"
        minSdk = 31
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }

    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
        if (hasDebugKeystore) {
            // 覆盖 AGP 默认的 debug 签名：让测试包也拥有一张可控的固定指纹证书，
            // 避免团队每台机器 ~/.android/debug.keystore 指纹不同带来的 SDK 授权烦恼。
            getByName("debug") {
                storeFile = rootProject.file(keystoreProps.getProperty("debugStoreFile"))
                storePassword = keystoreProps.getProperty("debugStorePassword")
                keyAlias = keystoreProps.getProperty("debugKeyAlias")
                keyPassword = keystoreProps.getProperty("debugKeyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 不显式覆盖 signingConfig：
            //   - hasDebugKeystore=true  → 上面已改写默认 debug signingConfig
            //   - hasDebugKeystore=false → 继续走 AGP 自动生成的 ~/.android/debug.keystore
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("en", "zh-rCN", "zh-rTW")
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.all {
            it.systemProperty("maven.repo.local", testMavenRepository)
            // 默认不访问真实账号；仅显式给出私有配置文件时运行只读账号连接测试。
            providers.gradleProperty("s3ConnectionConfig").orNull?.let { path ->
                it.systemProperty("alioss.s3.connectionConfig", path)
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.documentfile)
    implementation(libs.google.material)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.aliyun.oss.android)
    implementation(libs.aws.s3)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

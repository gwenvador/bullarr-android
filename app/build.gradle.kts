import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clé de signature du projet : fichier pointé par la propriété Gradle bullarrSigningFile (hors du dépôt).
val bullarrSigning = (project.findProperty("bullarrSigningFile") as String?)?.let { path ->
    Properties().apply { file(path).inputStream().use { load(it) } }
}

android {
    namespace = "io.github.gwenvador.bullarr"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.gwenvador.bullarr"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.8"
    }

    signingConfigs {
        if (bullarrSigning != null) create("release") {
            storeFile = file(bullarrSigning.getProperty("bullarrStoreFile"))
            storePassword = bullarrSigning.getProperty("bullarrStorePassword")
            keyAlias = bullarrSigning.getProperty("bullarrKeyAlias")
            keyPassword = bullarrSigning.getProperty("bullarrKeyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sans clé configurée, la release reste non signée : jamais de signature silencieuse avec la clé de debug.
            signingConfig = if (bullarrSigning != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui:1.10.0")
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.security:security-crypto:1.1.0")
    // Avatars proposés par le serveur (SVG).
    implementation("com.caverock:androidsvg-aar:1.4")
    // Tests unitaires (JVM) ; org.json réel, la version d'Android n'existant pas hors appareil.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

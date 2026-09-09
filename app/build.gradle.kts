import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signing credentials live in keystore.properties, which is git-ignored and
// never committed. Its absence is deliberately not an error: anyone cloning
// this repository can still build and run the app without owning the release
// key — assembleRelease simply produces an unsigned APK for them.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "code.opensource0000.justnotes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "code.opensource0000.justnotes"
        minSdk = 26
        targetSdk = 37
        // versionCode is what Android compares to decide "is this an
        // update?" — it must increase with every published build and never
        // go backwards. versionName is what people read, and follows semver
        // (see CHANGELOG.md). The two are deliberately independent: a
        // re-publish of the same version bumps the code, not the name.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v2 is what actually gets verified on minSdk 26; v3 adds the
                // ability to rotate to a new signing key later without every
                // installed copy refusing the update. Cheap to enable now,
                // impossible to add retroactively once versions are out.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8: shrinks and obfuscates. It matters more here than the APK
            // size suggests — material-icons-extended alone carries thousands
            // of vector icons of which this app draws about thirty, and with
            // optimization off every one of them shipped.
            //
            // Anything reached only from native code has to be kept by hand;
            // see app/src/main/keepRules/rules.keep for Vosk and JNA.
            optimization {
                enable = true
            }
            // Null when keystore.properties is absent, which leaves the APK
            // unsigned rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Lets a debug build sit alongside an installed release one.
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    bundle {
        language {
            // Mandatory here, and the failure is invisible in testing. Play
            // normally strips an AAB down to the device's system language —
            // but this app switches its own language from Settings, so a
            // stripped install would simply have no resources for the other
            // one and the picker would silently do nothing. Only ever shows up
            // after publishing, never in a locally built APK.
            enableSplit = false
        }
    }
}

// The licence texts have to ship inside the APK, not just sit in the
// repository: Apache 2.0 asks that recipients of the *work* get a copy of the
// licence and the NOTICE, and someone installing the APK never sees this repo.
//
// Copied at build time rather than duplicated into src/main/assets, so there
// is one source of truth. Two files that must say the same thing, maintained
// by hand, eventually stop saying the same thing.
// Registered through the Variant API rather than sourceSets.assets.srcDir():
// AGP rejects a Provider there, because it cannot tell generated files from
// hand-written ones, and wiring it that way would silently lose the task
// dependency anyway.
abstract class CopyLicenceAssets : DefaultTask() {
    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyThem() {
        val target = outputDirectory.get().asFile
        target.mkdirs()
        sourceFiles.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

androidComponents.onVariants { variant ->
    val copyTask = tasks.register<CopyLicenceAssets>(
        "copy${variant.name.replaceFirstChar { it.uppercase() }}LicenceAssets"
    ) {
        sourceFiles.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(
        copyTask,
        CopyLicenceAssets::outputDirectory
    )
}

// Where Room writes the exported schema (see JustNotesDatabase.exportSchema).
// These JSON files are committed on purpose: they are the baseline for every
// future migration.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    // Forces a modern androidx.fragment over the ancient 1.2.5 that biometric:1.1.0
    // pulls in transitively — that old version crashes any rememberLauncherForActivityResult()
    // call on our FragmentActivity with "Can only use lower 16 bits for requestCode".
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.alphacephei.vosk.android)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
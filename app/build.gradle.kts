import org.gradle.api.GradleException
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

private val supportedAppLocales = linkedMapOf(
    "SYSTEM" to null,
    "ENGLISH" to "en",
    "RUSSIAN" to "ru",
    "GERMAN" to "de",
)

private val playSigningPropertiesFile = rootProject.file("signing.properties")
private val playSigningProperties = Properties().apply {
    if (playSigningPropertiesFile.isFile) {
        playSigningPropertiesFile.inputStream().use(::load)
    }
}
private val playSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
private val hasCompletePlaySigning = playSigningKeys.all { key ->
    !playSigningProperties.getProperty(key).isNullOrBlank()
}
private val requirePlaySigning = providers.gradleProperty("requirePlaySigning")
    .map { value -> value.toBooleanStrictOrNull() ?: false }
    .getOrElse(false)

if (playSigningPropertiesFile.exists() && !hasCompletePlaySigning) {
    throw GradleException(
        "signing.properties exists but does not define every required Play signing value. " +
            "See signing.properties.example.",
    )
}
if (requirePlaySigning && !hasCompletePlaySigning) {
    throw GradleException(
        "A signed Play bundle was requested, but signing.properties is unavailable or incomplete. " +
            "See signing.properties.example.",
    )
}

private fun localizedResourceKeys(file: File): Map<String, String> {
    val document = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(file)

    val keys = linkedMapOf<String, String>()
    val nodes = document.documentElement.childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeName !in setOf("string", "plurals")) continue

        val name = node.attributes?.getNamedItem("name")?.nodeValue
            ?: throw GradleException(file.path + ": " + node.nodeName + " has no name")
        val prior = keys.put(name, node.nodeName)
        if (prior != null) {
            throw GradleException(
                file.path + ": duplicate resource key '" + name + "' (" + prior + " and " + node.nodeName + ")",
            )
        }
    }
    return keys
}

private fun localizedDirectoryKeys(directory: File): Map<String, String> {
    val keys = linkedMapOf<String, String>()
    directory.listFiles { file -> file.extension == "xml" }.orEmpty().sortedBy { it.name }.forEach { file ->
        localizedResourceKeys(file).forEach { (key, kind) ->
            if (keys.put(key, kind) != null) {
                throw GradleException("Duplicate localized resource key '$key' in ${directory.path}")
            }
        }
    }
    return keys
}

val verifyLocalizationIntegrity by tasks.registering {
    group = "verification"
    description = "Verifies translated string keys and the supported application locale configuration."

    val defaultStrings = layout.projectDirectory.file("src/main/res/values/strings.xml")
    val russianStrings = layout.projectDirectory.file("src/main/res/values-ru/strings.xml")
    val germanStrings = layout.projectDirectory.file("src/main/res/values-de/strings.xml")
    val localeConfig = layout.projectDirectory.file("src/main/res/xml/locales_config.xml")
    val resourceDefaults = layout.projectDirectory.file("src/main/res/resources.properties")
    val languageSelector = layout.projectDirectory.file("src/main/kotlin/com/hcloudcontrol/MainActivity.kt")

    inputs.files(
        defaultStrings,
        russianStrings,
        germanStrings,
        localeConfig,
        resourceDefaults,
        languageSelector,
    )
    inputs.files(fileTree("src/main/res/values") { include("*.xml") })
    inputs.files(fileTree("src/main/res/values-ru") { include("*.xml") })
    inputs.files(fileTree("src/main/res/values-de") { include("*.xml") })

    doLast {
        val base = localizedDirectoryKeys(defaultStrings.asFile.parentFile)
        val translations = linkedMapOf(
            "ru" to localizedDirectoryKeys(russianStrings.asFile.parentFile),
            "de" to localizedDirectoryKeys(germanStrings.asFile.parentFile),
        )

        translations.forEach { (locale, keys) ->
            val missing = base.keys - keys.keys
            val extra = keys.keys - base.keys
            val typeMismatches = base.keys.intersect(keys.keys)
                .filter { key -> base.getValue(key) != keys.getValue(key) }

            if (missing.isNotEmpty() || extra.isNotEmpty() || typeMismatches.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        append("Localization parity failure for '" + locale + "'.")
                        if (missing.isNotEmpty()) append(" Missing: " + missing.sorted().joinToString() + ".")
                        if (extra.isNotEmpty()) append(" Unexpected: " + extra.sorted().joinToString() + ".")
                        if (typeMismatches.isNotEmpty()) {
                            append(" Resource type mismatch: " + typeMismatches.sorted().joinToString() + ".")
                        }
                    },
                )
            }
        }

        val localeDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(localeConfig.asFile)
        val configuredLocales = buildSet {
            val nodes = localeDocument.getElementsByTagName("locale")
            for (index in 0 until nodes.length) {
                add(nodes.item(index).attributes.getNamedItem("android:name")?.nodeValue
                    ?: throw GradleException(localeConfig.asFile.path + ": locale without android:name"))
            }
        }
        val expectedLocaleTags = supportedAppLocales.values.filterNotNull().toSet()
        if (configuredLocales != expectedLocaleTags) {
            throw GradleException(
                "Locale config must contain exactly " + expectedLocaleTags.sorted() +
                    "; found " + configuredLocales.sorted() + ".",
            )
        }

        val resourceProperties = Properties().apply {
            resourceDefaults.asFile.inputStream().use(::load)
        }
        if (resourceProperties.getProperty("unqualifiedResLocale") != "en") {
            throw GradleException("resources.properties must declare unqualifiedResLocale=en for the default language.")
        }

        val selectorSource = languageSelector.asFile.readText()
        val actualSelectorTags = Regex(
            """(?m)^\s*(SYSTEM|ENGLISH|RUSSIAN|GERMAN)\(R\.string\.language_\w+,\s*(null|"[a-z]+")\),?\s*$""",
        ).findAll(selectorSource).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"").takeUnless { it == "null" }
        }
        if (actualSelectorTags != supportedAppLocales) {
            throw GradleException(
                "Language selector must expose only system default, en, ru and de; found " + actualSelectorTags + ".",
            )
        }
    }
}

android {
    namespace = "com.hcloudcontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hcloudcontrol"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    if (hasCompletePlaySigning) {
        signingConfigs.create("playRelease") {
            storeFile = rootProject.file(playSigningProperties.getProperty("storeFile"))
            storePassword = playSigningProperties.getProperty("storePassword")
            keyAlias = playSigningProperties.getProperty("keyAlias")
            keyPassword = playSigningProperties.getProperty("keyPassword")
        }
        buildTypes.named("release") {
            signingConfig = signingConfigs.getByName("playRelease")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyLocalizationIntegrity)
}

val verifyPlayReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails unless release signing has been configured outside source control."
    doLast {
        if (!hasCompletePlaySigning) {
            throw GradleException(
                "Play release signing is not configured. Copy signing.properties.example to the ignored " +
                    "signing.properties file and provide the upload-key values outside the repository.",
            )
        }
    }
}

if (requirePlaySigning) {
    tasks.matching { task -> task.name == "bundleRelease" }.configureEach {
        dependsOn(verifyPlayReleaseSigning)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// Verify the actual merged declaration, without adding a runtime/SDK test dependency.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val variant = when (name) {
        "testDebugUnitTest" -> "debug"
        "testReleaseUnitTest" -> "release"
        else -> null
    }
    if (variant != null) {
        val manifestTask = "process${variant.replaceFirstChar { it.uppercase() }}Manifest"
        dependsOn(manifestTask)
        systemProperty("hcloud.mergedManifest", layout.buildDirectory.file(
            "intermediates/merged_manifests/$variant/$manifestTask/AndroidManifest.xml").get().asFile.absolutePath)
    }
}

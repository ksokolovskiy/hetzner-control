plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // The checked-in WSL toolchain is JDK 21; JVM bytecode compatibility is
    // controlled by Android consumers, while this pure Kotlin module compiles here.
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

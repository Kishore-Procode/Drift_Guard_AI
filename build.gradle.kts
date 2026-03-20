plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.driftdetector"
version = "1.0.0"

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // Kotlin & Coroutines
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // HTTP Client with serialization
    implementation("io.ktor:ktor-client-core:2.3.4")
    implementation("io.ktor:ktor-client-java:2.3.4")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")
    implementation("io.ktor:ktor-client-websockets:2.3.4")
    
    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.0.0")
    
    // Math & Statistics
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // DateTime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    
    // File handling
    implementation("commons-io:commons-io:2.13.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:2.3.4")
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.register<JavaExec>("runSimulationProbe") {
    group = "verification"
    description = "Runs deterministic drift before/after probe on demo CSV files"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.driftdetector.desktop.tools.SimulationProbeKt")
}

compose.desktop {
    application {
        mainClass = "com.driftdetector.desktop.MainKt"
        
        nativeDistributions {
            packageName = "DriftGuardAI"
            packageVersion = "1.0.0"
            description = "ML Drift Monitoring and Patching System"
            copyright = "© 2024 DriftGuardAI"
            vendor = "DriftGuardAI"
            
            windows {
                packageVersion = "1.0.0"
                msiPackageVersion = "1.0.0"
                dirChooser = true
                perUserInstall = true
                includeAllModules = true
            }
        }
    }
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
}
group = "me.alak"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}
dependencies {
    testImplementation(kotlin("test-junit5"))
    implementation ("com.squareup.okhttp3:okhttp:3.10.0")
    implementation ("net.sourceforge.tess4j:tess4j:4.5.4")
    implementation ("org.slf4j:slf4j-api:1.7.25")
    implementation ("org.slf4j:slf4j-nop:1.7.25")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

}
tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "13"
}
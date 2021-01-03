import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.bundling.Jar

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
    implementation ("org.slf4j:slf4j-simple:1.7.30")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
}
tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    register<Jar>("buildFatJar") {
        manifest {
            attributes["Main-Class"] = "crossword.MainKt"
        }
        from(configurations.compileClasspath.get().files.map { if (it.isDirectory) it else zipTree(it) })
        with(jar.get() as CopySpec)
        archiveBaseName.set("${project.name}-fat")}
}


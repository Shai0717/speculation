import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.20"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

allprojects {
    repositories {
        mavenCentral()
    }

    tasks {
        withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_25)
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
        testImplementation("org.mockito:mockito-core:3.6.28")
    }

    tasks {
        test {
            useJUnitPlatform()
        }
    }
}



tasks {
    val resourcepacksFolder = file("resourcepacks")
    val resourcepacksTasks = resourcepacksFolder.listFiles { file -> file.isDirectory }?.map { folder ->
        register<Zip>("resourcepacksZip-${folder.name}") {
            from(folder)
            include("*")
            include("*/**")
            archiveFileName.set(if (folder.name == "speculation") "resourcepack.zip" else "${folder.name}.zip")
            destinationDirectory.set(file("build/libs/"))

            doLast {
                val file = archiveFile.get().asFile
                val bytes = MessageDigest.getInstance("SHA-1").digest(file.readBytes())
                val string = bytes.joinToString("") { "%02X".format(it) }

                println(string)
            }
        }
    }

    register("resourcepacksZip") {
        resourcepacksTasks?.forEach { dependsOn(it) }
    }

    register<Zip>("runZip") {
        from(file("run"))
        include("*")
        include("*/**")
        archiveFileName.set("server.zip")
        destinationDirectory.set(file("build/libs/"))
    }


}
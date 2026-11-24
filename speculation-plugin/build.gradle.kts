val core = project(":${rootProject.name}-core")

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(core)
    compileOnly("io.github.Shai0717:Kolor:1.0.0")
    compileOnly("io.papermc.paper:paper-api:26.3.build.26-alpha")
}

val pluginName = rootProject.name.split('-').joinToString("") { name ->
    name.replaceFirstChar { it.uppercase() }
}
val packageName = rootProject.name.replace("-", "")
extra.set("pluginName", pluginName)
extra.set("packageName", packageName)

tasks {
    processResources {
        filesMatching("*.yml") {
            expand(project.properties)
            expand(extra.properties)
        }
    }

    val paperJar = register<Jar>("paperJar") {
        dependsOn(project.tasks.getByName("classes"), core.tasks.getByName("classes"))
        archiveBaseName.set(pluginName)
        archiveVersion.set("")
        archiveFileName.set("$pluginName.jar")
        destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))

        (listOf(project, core) + core.subprojects).forEach {
            from(it.sourceSets["main"].output)
        }
    }

    named("assemble") {
        dependsOn(paperJar)
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("java")
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.3.1"
    id("com.willfp.libreforge-gradle-plugin") version "2.0.0"
}

group = "com.willfp"
version = findProperty("version")!!
val libreforgeVersion = findProperty("libreforge-version")
val ecoVersion = findProperty("eco-version")

base {
    archivesName.set(project.name)
}

dependencies {
    project.project(project(":eco-core").path).subprojects {
        implementation(this)
    }
}

java {
    withJavadocJar()
}

publishing {
    publications {
        // maven-private: only the shaded jar
        create<MavenPublication>("private") {
            artifactId = rootProject.name
        }
        // maven-releases + GitHub: full set (none, all, sources, javadoc)
        create<MavenPublication>("release") {
            artifactId = rootProject.name
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "Auxilor"
            url = uri("https://repo.auxilor.io/repository/maven-private/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
        maven {
            name = "AuxilorReleases"
            url = uri("https://repo.auxilor.io/repository/maven-releases/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("private") {
        artifact(tasks.named("libreforgeJar"))
    }
}

tasks.matching { it.name.startsWith("generatePomFileFor") }.configureEach {
    mustRunAfter(tasks.named("clean"))
}
tasks.register("publishToAuxilor") {
    dependsOn(
        "publishPrivatePublicationToAuxilorRepository",
        "publishReleasePublicationToAuxilorReleasesRepository",
    )
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradleup.shadow")

    repositories {
        mavenLocal()
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.auxilor.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://mvn.lumine.io/repository/maven-public/")
    }

    dependencies {
        compileOnly("com.willfp:eco:$ecoVersion")
        compileOnly("org.jetbrains:annotations:26.0.2")
        compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    }

    java {
        withSourcesJar()
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks {
        shadowJar {
            exclude("META-INF/**")
            relocate("com.willfp.libreforge.loader", "com.willfp.ecopets.libreforge.loader")
            relocate("com.willfp.ecomponent", "com.willfp.ecopets.ecomponent")
            relocate("com.willfp.modelenginebridge", "com.willfp.ecopets.modelenginebridge")
            relocate("kotlin", "com.willfp.eco.libs.kotlin")
            relocate("kotlin.jvm", "com.willfp.eco.libs.kotlin.jvm")
            relocate("kotlin.coroutines", "com.willfp.eco.libs.kotlin.coroutines")
            relocate("kotlin.reflect", "com.willfp.eco.libs.kotlin.reflect")
        }

        compileKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)

            }
        }

        compileJava {
            options.isDeprecation = true
            options.encoding = "UTF-8"

            dependsOn(clean)
        }

        processResources {
            filesMatching(listOf("**plugin.yml", "**eco.yml")) {
                expand(
                    "version" to project.version,
                    "libreforgeVersion" to libreforgeVersion!!,
                    "pluginName" to rootProject.name
                )
            }
        }

        build {
            dependsOn(shadowJar)
        }
    }
}

val libreforgeShadowFileName = "libreforge-$libreforgeVersion-shadow.jar"

tasks.register("bundleLibreforge") {
    description = "Inject the libreforge shadow jar into the distributable jar"
    dependsOn("libreforgeJar")

    doLast {
        val shadow = file(
            (findProperty("libreforgeShadowJar") as String?) ?: "libs/$libreforgeShadowFileName"
        )

        if (!shadow.isFile) {
            throw GradleException(
                """
                Missing $libreforgeShadowFileName.

                The distributable jar must contain libreforge at its root, otherwise the
                server refuses to enable EcoPets and no libreforge plugin ever loads.
                The Gradle plugin only fetches it from the private Auxilor repository,
                which needs MAVEN_USERNAME and MAVEN_PASSWORD.

                Without those credentials, extract it from an official EcoPets jar of the
                same version and drop it in libs/, or point at it explicitly:
                    ./gradlew bundleLibreforge -PlibreforgeShadowJar=/path/to/$libreforgeShadowFileName
                """.trimIndent()
            )
        }

        val target = file("bin/${rootProject.name} v$version.jar")
        if (!target.isFile) {
            throw GradleException("Expected ${target.path} to exist, run libreforgeJar first")
        }

        val platform = layout.buildDirectory.file("tmp/platform").get().asFile
        platform.parentFile.mkdirs()
        platform.writeText("platform=SPIGOT\n")

        ant.withGroovyBuilder {
            "zip"("destfile" to target, "update" to true) {
                "fileset"("file" to shadow.absolutePath)
                "fileset"("file" to platform.absolutePath)
            }
        }

        logger.lifecycle("Bundled $libreforgeShadowFileName into ${target.name}")
    }
}

tasks.matching { it.name == "libreforgeJar" }.configureEach {
    finalizedBy("bundleLibreforge")
}

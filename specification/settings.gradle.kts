rootProject.name = "sparkplug-spec"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.asciidoctor.jvm.base") version "${extra["plugin.asciidoctor.version"]}"
        id("com.github.sgtsilvio.gradle.metadata") version "${extra["plugin.metadata.version"]}"
    }
}

buildscript {
    repositories {
        mavenCentral()
    }

    val xalanVersion = extra["xalan.version"]

    dependencies {
        //classpath("xalan:xalan:$xalanVersion")
    }
}
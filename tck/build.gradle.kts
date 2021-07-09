import de.undercouch.gradle.tasks.download.Download
import nl.javadude.gradle.plugins.license.DownloadLicensesExtension.license

plugins {
    id("java")
    id("com.hivemq.extension")
    id("com.github.hierynomus.license-report")
    id("com.github.sgtsilvio.gradle.utf8")
    id("de.undercouch.download")
    //checkstyle
}


/* ******************** metadata ******************** */

group = "org.eclipse.sparkplug"
description = "Technology Compatibility Kit for Eclipse MQTT Sparkplug"

hivemqExtension {
    name = "Sparkplug TCK Tests"
    author = "Eclipse"
    priority = 0
    startPriority = 1000
    mainClass = "org.eclipse.sparkplug.tck.SparkplugHiveMQExtension"
    sdkVersion = "4.4.4"
}


/* ******************** spec dependency ******************** */
val specFolderName = "specification"
val auditCreationTaskName = "xsltAudit"


/* ******************** extension related ******************** */

tasks.hivemqExtensionResources {
    dependsOn(gradle.includedBuild(specFolderName).task(":${auditCreationTaskName}"))
    dependsOn("downloadLicenses")

    //not sure why the same file was copied twice with different classifiers.
    from(gradle.includedBuild(specFolderName).projectDir.resolve("build/tck-audit/tck-audit.xml")) {
        rename { "tck-audit-suite.xml" }
        into("coverage")
    }
    from(gradle.includedBuild(specFolderName).projectDir.resolve("build/tck-audit/tck-audit.xml")) {
        rename { "tck-audit-audit.xml" }
        into("coverage")
    }
    from(buildDir.resolve("coverage-report")) {
        into("coverage")
    }
    from("LICENSE")
    from(buildDir.resolve("reports/license/license-dependency.html")) {
        into("third-party-licenses")
    }
}


/* ******************** dependencies ******************** */

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repository.jboss.org/nexus/content/groups/public-jboss/")
    }
}

dependencies {
    annotationProcessor("org.jboss.test-audit:jboss-test-audit-impl:${property("jboss.test-audit.version")}")

    compileOnly("org.slf4j:slf4j-api:${property("slf4j.version")}")
    implementation("org.slf4j:slf4j-simple:${property("slf4j.version")}")

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:${property("paho.version")}")
    implementation("javax.annotation:javax.annotation-api:${property("javax.annotation.version")}")
    implementation("javax.validation:validation-api:${property("javax.validation.version")}")
    implementation("com.google.code.gson:gson:${property("gson.version")}")
    implementation("org.hibernate.beanvalidation.tck:beanvalidation-tck-tests:${property("beanvalidation.tck.version")}")
    implementation("org.junit.jupiter:junit-jupiter-params:${property("junit-jupiter.version")}")
    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:${property("junit-jupiter.version")}")
    implementation("jakarta.annotation:jakarta.annotation-api:${property("jakarta.annotation.version")}")
    implementation("jakarta.validation:jakarta.validation-api:${property("jakarta.validation.version")}")
    
    implementation("org.eclipse.tahu:tahu-java:${property("tahu.version")}")
}


/* ******************** test ******************** */

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit-jupiter.version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${property("junit-jupiter.version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${property("junit-jupiter.version")}")

    testImplementation("io.github.glytching:junit-extensions:${property("junit-extensions.version")}")
    testImplementation("org.mockito:mockito-core:${property("mockito.version")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${property("mockito.version")}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events("STARTED", "FAILED", "SKIPPED")
    }

    val inclusions = rootDir.resolve("inclusions.txt")
    val exclusions = rootDir.resolve("exclusions.txt")
    if (inclusions.exists()) {
        include(inclusions.readLines())
    } else if (exclusions.exists()) {
        exclude(exclusions.readLines())
    }
}


/* ******************** integration test ******************** */

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations {
    getByName("integrationTestImplementation").extendsFrom(testImplementation.get())
    getByName("integrationTestRuntimeOnly").extendsFrom(testRuntimeOnly.get())
}

dependencies {
    "integrationTestImplementation"("com.hivemq:hivemq-testcontainer-junit5:${property("hivemq-testcontainer.version")}")
    "integrationTestImplementation"("org.testcontainers:testcontainers:${property("testcontainers.version")}")
}

val prepareExtensionTest by tasks.registering(Sync::class) {
    group = "hivemq extension"
    description = "Prepares the extension for integration testing."

    from(tasks.hivemqExtensionZip.map { zipTree(it.archiveFile) })
    into(buildDir.resolve("hivemq-extension-test"))
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs integration tests."

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    dependsOn(prepareExtensionTest)
}

tasks.check { dependsOn(integrationTest) }


/* ******************** license ******************** */

/*
<plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>license-maven-plugin</artifactId>
                <version>1.8</version>
                <executions>
                    <execution>
                        <id>add-third-party</id>
                        <phase>package</phase>
                        <goals>
                            <goal>add-third-party</goal>
                            <goal>download-licenses</goal>
                        </goals>
                        <configuration>
                            <useMissingFile>true</useMissingFile>
                            <excludedScopes>test</excludedScopes>
                            <excludedGroups> (org.eclipse.tahu*)</excludedGroups>
                            <licenseMerges>
                                <licenseMerge>The Apache Software License, Version
                                    2.0|Apache License, Version 2.0|Apache Public License
                                    2.0|Apache License 2.0|Apache Software License -
                                    Version 2.0</licenseMerge>
                            </licenseMerges>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
 */

downloadLicenses {
    val apache_2 = license("Apache License, Version 2.0", "https://opensource.org/licenses/Apache-2.0")
    val mit = license("MIT License", "https://opensource.org/licenses/MIT")
    val cddl_1_0 = license("CDDL, Version 1.0", "https://opensource.org/licenses/CDDL-1.0")
    val cddl_1_1 = license("CDDL, Version 1.1", "https://oss.oracle.com/licenses/CDDL+GPL-1.1")
    val lgpl_2_0 = license("LGPL, Version 2.0", "https://opensource.org/licenses/LGPL-2.0")
    val lgpl_2_1 = license("LGPL, Version 2.1", "https://opensource.org/licenses/LGPL-2.1")
    val lgpl_3_0 = license("LGPL, Version 3.0", "https://opensource.org/licenses/LGPL-3.0")
    val epl_1_0 = license("EPL, Version 1.0", "https://opensource.org/licenses/EPL-1.0")
    val epl_2_0 = license("EPL, Version 2.0", "https://opensource.org/licenses/EPL-2.0")
    val edl_1_0 = license("EDL, Version 1.0", "https://www.eclipse.org/org/documents/edl-v10.php")
    val bsd_3clause = license("BSD 3-Clause License", "https://opensource.org/licenses/BSD-3-Clause")
    val w3c = license("W3C License", "https://opensource.org/licenses/W3C")
    val cc0 = license("CC0", "https://creativecommons.org/publicdomain/zero/1.0/")

    aliases = mapOf(
            apache_2 to listOf("Apache 2",
                    "Apache 2.0",
                    "Apache License 2.0",
                    "Apache License, 2.0",
                    "Apache License v2.0",
                    "Apache License, Version 2",
                    "Apache License Version 2.0",
                    "Apache License, Version 2.0",
                    "Apache License, version 2.0",
                    "The Apache License, Version 2.0",
                    "Apache Software License - Version 2.0",
                    "Apache Software License, version 2.0",
                    "The Apache Software License, Version 2.0"),
            mit to listOf("MIT License",
                    "MIT license",
                    "The MIT License",
                    "The MIT License (MIT)"),
            cddl_1_0 to listOf("CDDL, Version 1.0",
                    "Common Development and Distribution License 1.0",
                    "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0",
                    license("CDDL", "https://glassfish.dev.java.net/public/CDDLv1.0.html")),
            cddl_1_1 to listOf("CDDL 1.1",
                    "CDDL, Version 1.1",
                    "Common Development And Distribution License 1.1",
                    "CDDL+GPL License",
                    "CDDL + GPLv2 with classpath exception",
                    "Dual license consisting of the CDDL v1.1 and GPL v2",
                    "CDDL or GPLv2 with exceptions",
                    "CDDL/GPLv2+CE"),
            lgpl_2_0 to listOf("LGPL, Version 2.0",
                    "GNU General Public License, version 2"),
            lgpl_2_1 to listOf("LGPL, Version 2.1",
                    "LGPL, version 2.1",
                    "GNU Lesser General Public License version 2.1 (LGPLv2.1)",
                    license("GNU Lesser General Public License", "http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")),
            lgpl_3_0 to listOf("LGPL, Version 3.0",
                    "Lesser General Public License, version 3 or greater"),
            epl_1_0 to listOf("EPL, Version 1.0",
                    "Eclipse Public License - v 1.0",
                    "Eclipse Public License - Version 1.0",
                    license("Eclipse Public License", "http://www.eclipse.org/legal/epl-v10.html")),
            epl_2_0 to listOf("EPL 2.0",
                    "EPL, Version 2.0"),
            edl_1_0 to listOf("EDL 1.0",
                    "EDL, Version 1.0",
                    "Eclipse Distribution License - v 1.0"),
            bsd_3clause to listOf("BSD 3-clause",
                    "BSD-3-Clause",
                    "BSD 3-Clause License",
                    "3-Clause BSD License",
                    "New BSD License",
                    license("BSD", "http://asm.ow2.org/license.html"),
                    license("BSD", "http://asm.objectweb.org/license.html"),
                    license("BSD", "LICENSE.txt")),
            w3c to listOf("W3C License",
                    "W3C Software Copyright Notice and License",
                    "The W3C Software License"),
            cc0 to listOf("CC0",
                    "Public Domain")
    )

    dependencyConfiguration = "runtimeClasspath"
    excludeDependencies = listOf(
            "org.eclipse.tahu:*:*"
    )


}


/* ******************** build process ******************** */

plugins.withId("java") {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    plugins.apply("com.github.sgtsilvio.gradle.utf8")
}

sourceSets {
    main {
        java.srcDir(buildDir.resolve("generated/sources/audit/"))
    }
}

//Fetches created tck-audit file from specification project.
tasks.register("audit") {
    dependsOn(gradle.includedBuild(specFolderName).task(":${auditCreationTaskName}"))

    doLast {
        org.jboss.test.audit.generate.SectionsClassGenerator.main(arrayOf(
                gradle.includedBuild(specFolderName)
                        .projectDir.resolve("build/tck-audit/tck-audit.xml")
                        .absolutePath,
                "org.eclipse.sparkplug.tck",
                buildDir.resolve("generated/sources/audit/").absolutePath)
        )
    }
}

//Creates coverage-report with jboss audit annotation processor
tasks.named("compileJava", JavaCompile::class.java) {
    dependsOn("audit")
    options.compilerArgs.addAll(listOf(
            "-AauditXml=${
                gradle.includedBuild(specFolderName)
                        .projectDir.resolve("build/tck-audit/tck-audit.xml")
                        .absolutePath
            }",
            "-AoutputDir=${buildDir.resolve("coverage-report").absolutePath}"))
}


/* ******************** debug run ******************** */

val downloadHivemqCe by tasks.registering(Download::class) {
    src("https://github.com/hivemq/hivemq-community-edition/releases/download/2021.1/hivemq-ce-2021.1.zip")
    dest(buildDir.resolve("hivemq-ce.zip"))
    overwrite(false)
}

val unzipHivemq by tasks.registering(Sync::class) {
    dependsOn(downloadHivemqCe)
    from(zipTree(buildDir.resolve("hivemq-ce.zip")))
    into({ temporaryDir })
    println(temporaryDir)
}

tasks.prepareHivemqHome {
    hivemqFolder.set(unzipHivemq.map { it.destinationDir.resolve("hivemq-ce-2021.1") } as Any)
}

tasks.runHivemqWithExtension {
    debugOptions {
        enabled.set(true)
    }
}


//Is this still necessary?
/*
<plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <executions>
                    <execution>
                        <id>unpack</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>unpack</goal>
                        </goals>
                        <configuration>
                            <artifactItems>
                                <artifactItem>
                                    <groupId>jakarta.validation</groupId>
                                    <artifactId>jakarta.validation-api</artifactId>
                                    <type>jar</type>
                                    <overWrite>false</overWrite>
                                    <outputDirectory>${project.build.directory}/validation-api</outputDirectory>
                                    <includes>**'/*.class,**/*.xml</includes>
                                </artifactItem>
                            </artifactItems>
                            <includes>**'/*.java</includes>
                            <excludes>**'/*.properties</excludes>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

 */
 */
 */


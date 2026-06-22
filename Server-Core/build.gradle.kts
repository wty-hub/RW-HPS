/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

/**
 * Fuck implementation
 */
dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinVersion}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation(project(":TimeTaskQuartz"))
    implementation(project(":ASM-Framework"))

    //implementation("com.github.minxyzgo.rw-injection:core:077d92e08c")
    //compileOnly("com.github.minxyzgo.rw-injection:source:master-SNAPSHOT")

    api("io.netty:netty-buffer:${Versions.nettyVersion}")
    api("io.netty:netty-codec:${Versions.nettyVersion}")
    api("io.netty:netty-codec-http:${Versions.nettyVersion}")
    api("io.netty:netty-handler:${Versions.nettyVersion}")
    api("io.netty:netty-transport:${Versions.nettyVersion}")
    api("io.netty:netty-transport-native-epoll:${Versions.nettyVersion}:linux-aarch_64")
    api("io.netty:netty-transport-native-epoll:${Versions.nettyVersion}:linux-x86_64")
    api("com.lmax:disruptor:3.4.4")


    compileOnly(fileTree(mapOf("dir" to "libs", "include" to "game-lib.jar")))
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to "slick.jar")))

    // Json 解析
    // 我建议使用 RW-HPS Json 方法 而不是直接使用依赖
    api("com.google.code.gson:gson:2.10")
    api("org.json:json:20240303")

    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.tukaani:xz:1.9")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") {
        exclude("org.jetbrains.kotlin")
    }
    api("com.vdurmont:emoji-java:5.1.1") {
        exclude("org.json")
    }

    //implementation("org.lionsoul:ip2region:1.7.2")
    implementation("net.renfei:ip2location:1.2.1")

    implementation("net.java.dev.jna:jna:5.14.0")

    implementation("org.jline:jline-reader:${Versions.jLineVersion}")
    implementation("org.jline:jline-terminal:${Versions.jLineVersion}") {
        exclude("org.jline", "jline-native")
    }
    implementation("org.jline:jline-terminal-jna:${Versions.jLineVersion}")

    implementation("it.unimi.dsi:fastutil-core:8.5.13")

    compileOnlyAndTest("org.graalvm.js:js:${Versions.graalvmVersion}")
    compileOnlyAndTest("org.graalvm.sdk:graal-sdk:${Versions.graalvmVersion}")
    compileOnlyAndTest("org.ow2.asm:asm:9.7")
    compileOnlyAndTest("org.ow2.asm:asm-tree:9.7")
    implementation(fileTree(mapOf("dir" to "libs", "include" to "wasm.jar")))

    testApi("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

tasks.jar {
    project.makeDependTree()

    manifest {
        attributes(mapOf("Implementation-Title" to "RW-HPS"))
    }
}

tasks.test {
    useJUnitPlatform()
}

fun DependencyHandler.compileOnlyAndTest(dependencyNotation: Any) {
    this.testImplementation(dependencyNotation)
    this.compileOnly(dependencyNotation)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.RW-HPS"
            artifactId = "Server-Core"
            description = "Dedicated to Rusted Warfare(RustedWarfare) High Performance Server"
            version = "1.0.0"

            from(components.getByName("java"))

            pom {
                scm {
                    url.set("https://github.com/deng-rui/RW-HPS")
                    connection.set("scm:https://github.com/deng-rui/RW-HPS.git")
                    developerConnection.set("scm:git@github.com:deng-rui/RW-HPS.git")
                }

                licenses {
                    license {
                        name.set("GNU AGPLv3")
                        url.set("https://github.com/deng-rui/RW-HPS/blob/master/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("RW-HPS")
                        name.set("RW-HPS Technologies")
                    }
                }

            }

            pom.withXml {
                val root = asNode()
                root.appendNode("description", project.description)
                root.appendNode("name", project.name)
                root.appendNode("url", "https://github.com/deng-rui/RW-HPS")
            }
        }
    }
}
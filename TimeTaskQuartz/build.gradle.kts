dependencies {
    // Users should not operate Quartz
    // Hence the RunTime
    implementation("org.quartz-scheduler:quartz:2.3.2") {
        // Stand-alone operation, does not require any persistence
        exclude("com.mchange", "c3p0")
        exclude("com.mchange", "mchange-commons-java")
        exclude("com.zaxxer", "HikariCP-java7")
    }
    // Quartz pulls in slf4j-api; provide a NOP binding to silence StaticLoggerBinder warnings
    implementation("org.slf4j:slf4j-nop:1.7.36")
}

tasks.jar {
    project.makeDependTree()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.RW-HPS"
            artifactId = "Server-TimeTaskQuartz"
            description = "Dedicated to Rusted Warfare(RustedWarfare) High Performance Server"

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
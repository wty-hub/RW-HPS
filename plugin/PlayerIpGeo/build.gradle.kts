dependencies {
    compileOnly(project(":Server-Core"))
    compileOnly("net.renfei:ip2location:1.2.1")
}

val ip2locationDest = file("src/main/resources/ip2location.7z")
val serverCoreDb = rootProject.file("Server-Core/src/main/resources/ip2location.7z")

tasks.register("downloadIp2Location") {
    group = "playeripgeo"
    description = "Download or sync IP2LOCATION-LITE-DB5 into plugin resources (env: IP2LOCATION_TOKEN for official download)"

    outputs.file(ip2locationDest)

    doLast {
        ip2locationDest.parentFile.mkdirs()
        val token = System.getenv("IP2LOCATION_TOKEN")

        if (!token.isNullOrBlank()) {
            val zipFile = layout.buildDirectory.file("tmp/IP2LOCATION-LITE-DB5.BIN.ZIP").get().asFile
            zipFile.parentFile.mkdirs()

            exec {
                commandLine(
                    "curl", "-fsSL",
                    "-o", zipFile.absolutePath,
                    "https://www.ip2location.com/download?token=$token&file=DB5LITEBIN"
                )
            }

            val binFile = layout.buildDirectory.file("tmp/IP2LOCATION-LITE-DB5.BIN").get().asFile
            exec {
                commandLine("unzip", "-o", zipFile.absolutePath, "-d", binFile.parentFile.absolutePath)
            }

            exec {
                commandLine(
                    "7z", "a", "-t7z", "-mx=9", ip2locationDest.absolutePath,
                    binFile.absolutePath
                )
            }
            logger.lifecycle("Downloaded IP2LOCATION-LITE-DB5 from ip2location.com -> ${ip2locationDest.absolutePath}")
        } else if (serverCoreDb.exists()) {
            serverCoreDb.copyTo(ip2locationDest, overwrite = true)
            logger.lifecycle("Synced ip2location.7z from Server-Core (${ip2locationDest.length()} bytes)")
            logger.lifecycle("Tip: set IP2LOCATION_TOKEN to fetch the latest DB from https://lite.ip2location.com/")
        } else {
            throw GradleException(
                "IP2Location database missing. Either place Server-Core/src/main/resources/ip2location.7z " +
                    "or set IP2LOCATION_TOKEN (free account at https://lite.ip2location.com/) and re-run downloadIp2Location."
            )
        }
    }
}

tasks.processResources {
    dependsOn("downloadIp2Location")
}

tasks.jar {
    archiveBaseName.set("PlayerIpGeo")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("copyToPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.file("data/plugins"))
}

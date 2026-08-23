dependencies {
    compileOnly(project(":Server-Core"))
    testImplementation(project(":Server-Core"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("TeamChange")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("copyToPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(rootProject.file("data/plugins"))
}

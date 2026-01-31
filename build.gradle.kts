plugins {
    java
}

group = "swagger2sqlmap"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(files("/Applications/Burp Suite Community Edition.app/Contents/Resources/app/burpsuite_community.jar"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })

    manifest {
        attributes(
            "Implementation-Title" to "Swagger2Sqlmap",
            "Implementation-Version" to project.version
        )
    }
}

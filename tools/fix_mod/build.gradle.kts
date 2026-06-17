// Temporary build to compile fixed SchematicImporter classes against Hytale SDK
plugins {
    id("com.azuredoom.hytale-tools") version "1.+"
    java
}

repositories {
    mavenCentral()
}

hytaleTools {
    javaVersion = 22
    hytaleVersion = "0.5.4"
    manifestServerVersion = "0.5.4"
}

// Just compile, nothing else
tasks.named("processResources") { enabled = false }
tasks.named("jar") { enabled = false }
tasks.named("runServer") { enabled = false }

sourceSets {
    main {
        java {
            srcDirs = listOf(file("src"))
        }
    }
}

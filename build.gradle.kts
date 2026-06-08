import org.gradle.internal.os.OperatingSystem
import java.net.URI
import java.security.MessageDigest

plugins {
    java
    application
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.jda.orrery"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

spotless {
    java {
        googleJavaFormat().aosp().formatJavadoc(false)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// LWJGL configuration
val lwjglVersion = "3.3.3"
val jomlVersion = "1.10.5"

// Platform detection
val os: OperatingSystem = OperatingSystem.current()
val lwjglNatives = when {
    os.isWindows -> "natives-windows"
    os.isLinux -> "natives-linux"
    os.isMacOsX -> {
        if (System.getProperty("os.arch").startsWith("aarch64")) {
            "natives-macos-arm64"
        } else {
            "natives-macos"
        }
    }
    else -> throw GradleException("Unsupported OS: ${os.name}")
}

repositories {
    mavenCentral()
}

dependencies {
    // LWJGL core
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-stb:$lwjglVersion") // For image loading
    implementation("org.lwjgl:lwjgl-jemalloc:$lwjglVersion") // High-performance memory allocator

    // LWJGL natives
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-jemalloc:$lwjglVersion:$lwjglNatives")

    // Maths library
    implementation("org.joml:joml:$jomlVersion")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

application {
    mainClass.set("com.jda.orrery.app.OrreryApplication")
}

// ────────────────────────────────────────────────────────────────────────
// Texture asset fetch — textures live in GitHub Releases, not in git.
// See gradle.properties for configuration.
// ────────────────────────────────────────────────────────────────────────

val texturesRepoOwner: String = findProperty("texturesRepoOwner")?.toString() ?: ""
val texturesVersion: String = findProperty("texturesVersion")?.toString() ?: ""
val texturesSha256: String = findProperty("texturesSha256")?.toString() ?: ""

tasks.register("fetchTextures") {
    group = "build setup"
    description = "Download and extract texture assets from the matching GitHub release."

    doLast {
        if (texturesVersion.isEmpty()) {
            throw GradleException(
                "texturesVersion is not set in gradle.properties."
            )
        }

        val texturesDir = layout.projectDirectory.dir("src/main/resources/textures").asFile
        val markerFile = File(texturesDir, ".textures-version")

        if (markerFile.exists() && markerFile.readText().trim() == texturesVersion) {
            logger.lifecycle("Textures already at $texturesVersion — skipping fetch.")
            return@doLast
        }

        if (texturesRepoOwner.isEmpty()) {
            throw GradleException(
                "texturesRepoOwner is not set in gradle.properties — cannot fetch " +
                "texture assets. Textures at $texturesDir are out of date or missing " +
                "(expected version $texturesVersion)."
            )
        }

        val tarballName = "textures-$texturesVersion.tar.gz"
        val url = "https://github.com/$texturesRepoOwner/orrery/releases/download/$texturesVersion/$tarballName"
        val tmpDir = layout.buildDirectory.dir("tmp").get().asFile
        tmpDir.mkdirs()
        val tarball = File(tmpDir, tarballName)

        logger.lifecycle("Fetching $url")
        try {
            URI.create(url).toURL().openStream().use { input ->
                tarball.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            tarball.delete()
            throw GradleException("Failed to download texture tarball: ${e.message}", e)
        }

        if (texturesSha256.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256")
            tarball.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha.equals(texturesSha256, ignoreCase = true)) {
                tarball.delete()
                throw GradleException(
                    "Texture tarball SHA-256 mismatch.\n" +
                    "  expected: $texturesSha256\n" +
                    "  got:      $actualSha"
                )
            }
            logger.lifecycle("SHA-256 verified.")
        } else {
            logger.warn(
                "No texturesSha256 set — skipping integrity check. " +
                "Set it in gradle.properties once the release asset is final."
            )
        }

        texturesDir.mkdirs()
        // Wipe previous contents (except .gitkeep) before extracting.
        texturesDir.listFiles()?.forEach { f ->
            if (f.name != ".gitkeep") f.deleteRecursively()
        }

        copy {
            from(tarTree(resources.gzip(tarball)))
            into(texturesDir)
        }

        markerFile.writeText(texturesVersion)
        logger.lifecycle("Textures extracted to $texturesDir")
    }
}

tasks.run.configure {
    // Fetch texture assets on first run (no-op if already at the right version).
    dependsOn("fetchTextures")

    // Memory settings
    jvmArgs("-Xmx2G")
    jvmArgs("-Xms1G")  // Start with 1GB to avoid early resizing
    
    // Use G1GC for better pause times
    jvmArgs("-XX:+UseG1GC")
    jvmArgs("-XX:MaxGCPauseMillis=10")  // Target 10ms max pause
    jvmArgs("-XX:+ParallelRefProcEnabled")  // Parallel reference processing
    
    // Enable assertions
    jvmArgs("-ea")

    // Better error reporting
    jvmArgs("-XX:+ShowCodeDetailsInExceptionMessages")

    // Debug mode — enable with -Pdebug. Wires both the LWJGL native debug flags
    // and the Java-side DEBUG_MODE gate in OrreryApplication (GL debug context + callbacks).
    if (project.hasProperty("debug")) {
        jvmArgs("-Dorg.lwjgl.util.Debug=true")
        jvmArgs("-Dorg.lwjgl.util.DebugLoader=true")
        jvmArgs("-Dorg.lwjgl.util.DebugAllocator=true")
        jvmArgs("-Dorrery.debug=true")
    }

    // Windowed mode: disable borderless fullscreen with -Pwindowed.
    if (project.hasProperty("windowed")) {
        jvmArgs("-Dorrery.fullscreen=false")
    }

    // Scroll zoom feel overrides with -PzoomSensitivity and -PzoomSmoothing
    // (see FrameController).
    if (project.hasProperty("zoomSensitivity")) {
        jvmArgs("-Dorrery.zoomSensitivity=${project.property("zoomSensitivity")}")
    }
    if (project.hasProperty("zoomSmoothing")) {
        jvmArgs("-Dorrery.zoomSmoothing=${project.property("zoomSmoothing")}")
    }

    // macOS specific - REQUIRED for GLFW
    if (os.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.test {
    useJUnitPlatform()
}

// Task to check if resources are properly packaged
tasks.register("checkResources") {
    doLast {
        val resourcesDir = sourceSets["main"].resources.srcDirs.first()
        logger.lifecycle("Resources directory: $resourcesDir")
        logger.lifecycle("Shader files:")
        file("$resourcesDir/shaders").listFiles()?.forEach {
            logger.lifecycle("  - ${it.name}")
        }
    }
}
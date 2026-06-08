plugins {
    // Auto-downloads a matching JDK when none is installed locally — both for
    // the java {} toolchain in build.gradle.kts and for keeping
    // gradle/gradle-daemon-jvm.properties download URLs up to date
    // (./gradlew updateDaemonJvm).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "orrery"

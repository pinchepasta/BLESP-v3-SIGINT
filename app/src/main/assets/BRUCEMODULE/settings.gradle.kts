pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required for com.github.mik3y:usb-serial-for-android
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "BruceRemoteControl"
include(":app")

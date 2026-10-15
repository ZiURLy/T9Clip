pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); maven("https://api.xposed.info/") }
}
rootProject.name = "T9Clip"
include(":app")

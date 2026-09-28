val annotationsVersion: String by project
val spigotVersion: String by project
val takionVersion: String by project

dependencies {
    api("org.jetbrains:annotations:$annotationsVersion")
    api("org.spigotmc:spigot-api:$spigotVersion")
    api("me.croabeast.takion:shaded:$takionVersion:all")
}

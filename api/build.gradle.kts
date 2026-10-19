val annotationsVersion = property("annotationsVersion") as String
val spigotVersion = property("spigotVersion") as String
val takionVersion = property("takionVersion") as String

dependencies {
    api("org.jetbrains:annotations:$annotationsVersion")
    api("org.spigotmc:spigot-api:$spigotVersion")
    api("me.croabeast.takion:shaded:$takionVersion:all")
}

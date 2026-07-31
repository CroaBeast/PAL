plugins {
    base
    id("io.freefair.lombok") version "9.4.0" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
}

allprojects {
    group = "com.bitaspire.pal"
    version = "0.1.0"

    repositories {
        mavenCentral()

        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
        maven("https://jitpack.io")
        maven("https://croabeast.github.io/repo/")
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "com.gradleup.shadow")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        options.compilerArgs.add("-Xlint:-options")
    }

    tasks.withType<Javadoc>().configureEach {
        isFailOnError = false

        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
            docEncoding = "UTF-8"

            if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_9))
                addBooleanOption("html5", true)
        }
    }

    dependencies {
        add("compileOnly", "org.jetbrains:annotations:26.0.2")
        add("annotationProcessor", "org.jetbrains:annotations:26.0.2")

        add("compileOnly", "org.projectlombok:lombok:1.18.44")
        add("annotationProcessor", "org.projectlombok:lombok:1.18.44")
    }
}

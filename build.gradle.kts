
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask

plugins {
    id("cn.elytra.gradle.conventions") version "1.2.1"
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnit()
}

val guideResourcePackSources = layout.projectDirectory.dir("src/main/resources")

tasks.withType<RunMinecraftTask>().configureEach {
    if (name.startsWith("runClient")) {
        extraJvmArgs.add(
            "-Dguideme.resourcePacks.sources=${guideResourcePackSources.asFile.absolutePath}",
        )
    }
}

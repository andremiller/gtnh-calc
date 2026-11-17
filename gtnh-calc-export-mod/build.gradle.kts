plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

repositories {
    maven { url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
    mavenCentral()
}

dependencies {
    compileOnly("com.github.GTNewHorizons:GT5-Unofficial:5.09.54.265:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.8.171-GTNH:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:CodeChickenCore:1.4.22:dev") { isTransitive = false }
    testImplementation("junit:junit:4.13.2")
    shadowImplementation("org.sejda.imageio:webp-imageio:0.1.6")
}

val verifyPackagedEncoder = tasks.register<JavaExec>("verifyPackagedEncoder") {
    dependsOn("reobfJar", "testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("tools.gtnh.calc.export.PackagedEncoderCheck")
    args(layout.buildDirectory.file("libs/${providers.gradleProperty("modId").get()}-${providers.gradleProperty("modVersion").get()}.jar").get().asFile.absolutePath)
}

tasks.named("build") {
    dependsOn(verifyPackagedEncoder)
}

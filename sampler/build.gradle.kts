plugins {
    application
}

dependencies {
    implementation(project(":extractor"))

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.github.luisr96.whathappenedhere.sampler.Sampler")
}

tasks.test {
    useJUnitPlatform()
}

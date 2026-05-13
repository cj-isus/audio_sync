allprojects {
    group = "ru.audiosynchronizer"
    version = "2.0.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

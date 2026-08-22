plugins { `kotlin-dsl` }

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
}

kotlin { jvmToolchain(21) }

# SMS → ntfy Kotlin Multiplatform

`shared` contains platform-neutral models and a Ktor `NtfyClient`, built for Android and desktop JVM. `androidApp` demonstrates Android SMS reception using the shared forwarding client.

Use JDK 17 and run `./gradlew :shared:allTests` and `./gradlew :androidApp:assembleDebug`.

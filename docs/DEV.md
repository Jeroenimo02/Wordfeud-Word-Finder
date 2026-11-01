# Ontwikkelen

## Reguliere commando's (CI en machines met Android SDK)

```sh
./gradlew :extensions:extension:testDebugUnitTest   # JVM-unittests van de extensie
./gradlew buildAndroid                              # bouwt patches/build/libs/patches-*.mpp
```

De Morphe Gradle-plugin (`app.morphe.patches` 2.1.0) komt van GitHub Packages
(`maven.pkg.github.com/MorpheApp/registry`) en vereist een token (`gpr.user`/`gpr.key` in
`~/.gradle/gradle.properties`, of `GITHUB_ACTOR`/`GITHUB_TOKEN`).

## Tests zonder Android SDK

Voor omgevingen zonder Android SDK en zonder toegang tot GitHub Packages staat in
`tools/jvm-tests/` een zelfstandige Gradle-build (gewone `java`-plugin, Java 17,
Maven Central). Die compileert `extensions/extension/src/main/java` en draait
`extensions/extension/src/test/java` (+ `src/test/resources`) met JUnit 4.13.2 en `org.json`.

```sh
gradle -p tools/jvm-tests test
```

- Klassen die `android.*` gebruiken kunnen niet op de gewone JVM compileren. Het pakket `**/ui/**`
  wordt daarom uitgesloten; extra bestanden zet je expliciet in de lijst `androidOnly` in
  `tools/jvm-tests/build.gradle.kts`.
- Compilatiecontrole van alle extensiebronnen (inclusief `ui/`) tegen `android.jar` (platform 34):

  ```sh
  tools/jvm-tests/android-compile-check.sh
  ```

  Het script downloadt `android.jar` eenmalig naar `tools/jvm-tests/.cache/` (genegeerd door git) en
  draait `javac` over alle bronnen. Dit controleert alleen of het compileert, niet of het draait.

## Lokaal patchen

**Nog niet geverifieerd op een toestel.** Volgens de README en documentatie van Morphe Desktop
(`MorpheApp/morphe-cli`, vereist Java 21+):

```sh
java -jar morphe-desktop-*-all.jar patch -p patches/build/libs/patches-*.mpp \
  -o wordfeud-patched.apk wordfeud-5.7.1.apkm
# Optioneel direct installeren via ADB: voeg -i toe. Alleen versiecontrole overslaan: -f
```

De GUI kan dezelfde `.mpp` laden (lokale bron) en accepteert `.apk`, `.apkm`, `.xapk` en `.apks`.
Het resultaat is `*-patched.apk`, getekend met de gedeelde `morphe.keystore`.

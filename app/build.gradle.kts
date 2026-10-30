import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  id("jacoco")
}

android {
  namespace = "com.alauncher.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.alauncher.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 39
    versionName = "1.4.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }


  signingConfigs {
    create("release") {
      val env = Properties()
      val envFile = rootProject.file(".env")
      if (envFile.exists()) {
        envFile.inputStream().use { 
          env.load(it) 
        }
      }

      val keystorePath = (System.getenv("KEYSTORE_PATH")
        ?: env.getProperty("KEYSTORE_PATH")
        ?: project.findProperty("KEYSTORE_PATH")?.toString()
        ?: "")
      if (keystorePath.isNotBlank()) {
        storeFile = file(keystorePath)
      }
      storePassword = (System.getenv("STORE_PASSWORD")
        ?: env.getProperty("STORE_PASSWORD")
        ?: project.findProperty("STORE_PASSWORD")?.toString()
        ?: "")
      keyAlias = (System.getenv("KEY_ALIAS")
        ?: env.getProperty("KEY_ALIAS")
        ?: project.findProperty("KEY_ALIAS")?.toString()
        ?: "upload")
      keyPassword = (System.getenv("KEY_PASSWORD")
        ?: env.getProperty("KEY_PASSWORD")
        ?: project.findProperty("KEY_PASSWORD")?.toString()
        ?: "")
    }
    val localDebugKeystore = file("${rootDir}/debug.keystore")
    if (localDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = localDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (signingConfigs.getByName("release").storeFile != null) {
        signingConfig = signingConfigs.getByName("release")
      }
      ndk {
        debugSymbolLevel = "SYMBOL_TABLE"
      }
    }
    debug {
      if (signingConfigs.findByName("debugConfig") != null) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
      enableUnitTestCoverage = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources {
      excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/*.version",
        "/META-INF/androidx/**",
        "/META-INF/LICENSE*",
        "/META-INF/NOTICE*"
      )
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    abortOnError = true
    checkAllWarnings = true
    checkDependencies = true
    enable += setOf("UnusedResources", "UnusedIds")
    disable += setOf(
      "GradleDependency",
      "NewerVersionAvailable",
      "AndroidGradlePluginVersion",
      "OldTargetApi",
      "ModifierParameter",
      "LogNotTimber",
      "UseKtx",
      "SyntheticAccessor"
    )
  }
}

composeCompiler {
  reportsDestination = layout.buildDirectory.dir("compose_compiler")
  metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(libs.timber)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.leakcanary.android)
  "ksp"(libs.androidx.room.compiler)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

jacoco {
  toolVersion = "0.8.15"
}

tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  group = "verification"
  description = "Generate Jacoco code coverage report."

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  val fileFilter = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "android/**/*.*",
    "**/ui/**",
    "**/service/**",
    "**/receiver/**",
    "**/*ComposableSingletons*.*",
    "**/*_Factory*.*",
    "**/*_HiltModules*.*",
    "**/*_Impl*.*"
  )

  val kotlinClasses = fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
    exclude(fileFilter)
  }

  sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
  classDirectories.setFrom(files(kotlinClasses))
  executionData.setFrom(fileTree(layout.buildDirectory) {
    include(
      "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
      "jacoco/testDebugUnitTest.exec"
    )
  })
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  dependsOn("jacocoTestReport")
  group = "verification"
  description = "Verifies overall project code coverage meets baseline floor (45%)."

  val fileFilter = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "android/**/*.*",
    "**/ui/**",
    "**/service/**",
    "**/receiver/**",
    "**/*ComposableSingletons*.*",
    "**/*_Factory*.*",
    "**/*_HiltModules*.*",
    "**/*_Impl*.*"
  )

  val kotlinClasses = fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
    exclude(fileFilter)
  }

  classDirectories.setFrom(files(kotlinClasses))
  executionData.setFrom(fileTree(layout.buildDirectory) {
    include(
      "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
      "jacoco/testDebugUnitTest.exec"
    )
  })

  val minThreshold = project.findProperty("minCoverage")?.toString() ?: "0.45"
  violationRules {
    rule {
      limit {
        minimum = minThreshold.toBigDecimal()
      }
    }
  }
}

@UntrackedTask(because = "Verifies real-time git diff coverage against current commit/PR")
abstract class VerifyChangedCoverageTask : DefaultTask() {
  @get:Internal
  abstract val buildDir: DirectoryProperty

  @get:Internal
  abstract val rootDir: DirectoryProperty

  @get:Input
  @get:Optional
  abstract val minCoverageThreshold: Property<Double>

  @TaskAction
  fun verify() {
    val reportFile = File(buildDir.get().asFile, "reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    if (!reportFile.exists()) {
      println("⚠️ JaCoCo XML report not found at ${reportFile.path}, skipping changed code coverage verification.")
      return
    }

    val projectRoot = rootDir.get().asFile
    val isCI = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    val gitChanges = try {
      val cmd = if (isCI) {
        listOf("git", "diff", "--name-only", "origin/main...HEAD")
      } else {
        listOf("git", "status", "--short")
      }
      val pb = ProcessBuilder(cmd).directory(projectRoot).redirectErrorStream(true)
      pb.environment().remove("GIT_INDEX_FILE")
      pb.environment().remove("GIT_DIR")
      pb.environment().remove("GIT_WORK_TREE")
      pb.environment().remove("GIT_PREFIX")
      val proc = pb.start()
      val output = proc.inputStream.bufferedReader().readText().trim()
      proc.waitFor()
      if (output.isNotBlank()) {
        output.lines()
          .filter { !it.startsWith("error:") && !it.startsWith("warning:") && it.isNotBlank() }
          .map { it.trim().split(Regex("""\s+""")).last() }
      } else emptyList()
    } catch (_: Exception) {
      emptyList<String>()
    }

    fun isExcluded(path: String): Boolean {
      val norm = path.replace("\\", "/")
      return norm.contains("/ui/") ||
             norm.contains("/service/") ||
             norm.contains("/receiver/") ||
             norm.contains("/test/") ||
             norm.contains("/androidTest/") ||
             norm.endsWith("Activity.kt") ||
             norm.endsWith("Activity.java") ||
             norm.endsWith("Application.kt") ||
             (!norm.endsWith(".kt") && !norm.endsWith(".java"))
    }

    val changedCoreFiles = gitChanges
      .filter { (it.endsWith(".kt") || it.endsWith(".java")) && !isExcluded(it) }
      .map { File(it).name }
      .distinct()

    if (changedCoreFiles.isEmpty()) {
      println("✅ [verifyChangedCodeCoverage] No modified core logic files subject to coverage threshold. Check passed.")
      return
    }

    val content = reportFile.readText()
    val minThreshold = minCoverageThreshold.getOrElse(70.0)
    val violations = mutableListOf<String>()

    println("🔍 Verifying changed code coverage for ${changedCoreFiles.size} core file(s) (threshold >= ${minThreshold}%):")

    changedCoreFiles.forEach { fileName ->
      val escapedName = Regex.escape(fileName)
      val fileBlock = Regex("""<sourcefile name="$escapedName">(.*?)</sourcefile>""", RegexOption.DOT_MATCHES_ALL)
        .find(content)?.groupValues?.get(1)
      if (fileBlock == null) {
        violations.add("❌ $fileName: No coverage recorded in JaCoCo report (0.0% < ${minThreshold}%)")
      } else {
        val lineMatch = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""").findAll(fileBlock).lastOrNull()
        val missed = lineMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val covered = lineMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
        val total = missed + covered
        val linePct = if (total > 0) (covered * 100.0 / total) else 100.0

        if (linePct < minThreshold) {
          violations.add(String.format("❌ %s: %.1f%% lines covered (expected >= %.1f%%)", fileName, linePct, minThreshold))
        } else {
          println(String.format("   • %s: %.1f%% lines covered (PASS)", fileName, linePct))
        }
      }
    }

    if (violations.isNotEmpty()) {
      val msg = "Changed code coverage verification FAILED:\n" + violations.joinToString("\n")
      throw GradleException(msg)
    } else {
      println("✅ [verifyChangedCodeCoverage] All modified core logic files meet >= ${minThreshold}% coverage threshold!")
    }
  }
}

tasks.register<VerifyChangedCoverageTask>("verifyChangedCodeCoverage") {
  dependsOn("jacocoTestReport")
  group = "verification"
  description = "Verifies that newly modified Kotlin/Java source files in the current commit or PR meet at least 70% coverage."
  buildDir.set(layout.buildDirectory)
  rootDir.set(rootProject.layout.projectDirectory)
  minCoverageThreshold.set(project.findProperty("minChangedCoverage")?.toString()?.toDoubleOrNull() ?: 70.0)
}

tasks.register("preCommitCheck") {
  group = "verification"
  description = "Runs unit tests, coverage verification, Play Console audit, and Android lint before committing."
  dependsOn("testDebugUnitTest", "jacocoTestCoverageVerification", "verifyChangedCodeCoverage", "lintDebug", "qualitySummary")
}

@UntrackedTask(because = "Console reporter reading real-time git working tree status and build metrics")
abstract class QualitySummaryTask : DefaultTask() {
  @get:Internal
  abstract val buildDir: DirectoryProperty

  @get:Internal
  abstract val rootDir: DirectoryProperty

  @get:OutputFile
  abstract val reportFile: RegularFileProperty

  @TaskAction
  fun summarize() {
    val root = buildDir.get().asFile
    val projectRoot = rootDir.get().asFile
    val out = StringBuilder()
    fun log(line: String = "") {
      println(line)
      out.appendLine(line)
    }

    log("\n" + "=".repeat(60))
    log("📊 PRE-COMMIT QUALITY SUMMARY")
    log("=".repeat(60))

    // 1. Current Commit Changes
    val gitChanges = try {
      val pb = ProcessBuilder("git", "status", "--short")
        .directory(projectRoot)
        .redirectErrorStream(true)
      pb.environment().remove("GIT_INDEX_FILE")
      pb.environment().remove("GIT_DIR")
      pb.environment().remove("GIT_WORK_TREE")
      pb.environment().remove("GIT_PREFIX")
      val proc = pb.start()
      val output = proc.inputStream.bufferedReader().readText().trim()
      proc.waitFor()
      if (output.isNotBlank()) {
        output.lines().filter { !it.startsWith("error:") && !it.startsWith("warning:") }
      } else emptyList()
    } catch (_: Exception) {
      emptyList<String>()
    }

    if (gitChanges.isNotEmpty()) {
      log("📝 Current Changes (${gitChanges.size} file${if (gitChanges.size > 1) "s" else ""}):")
      gitChanges.take(10).forEach { line ->
        log("   • $line")
      }
      if (gitChanges.size > 10) {
        log("   ... and ${gitChanges.size - 10} more")
      }
      log()
    } else {
      log("📝 Current Changes: Working tree clean\n")
    }

    // 2. Unit Tests
    val testDir = File(root, "test-results/testDebugUnitTest")
    if (testDir.exists()) {
      var totalTests = 0
      var totalFailures = 0
      var totalErrors = 0
      var totalSkipped = 0
      testDir.listFiles { f -> f.extension == "xml" }?.forEach { f ->
        val text = f.readText()
        totalTests += Regex("""tests="(\d+)"""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        totalFailures += Regex("""failures="(\d+)"""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        totalErrors += Regex("""errors="(\d+)"""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        totalSkipped += Regex("""skipped="(\d+)"""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
      }
      val passed = totalTests - totalFailures - totalErrors - totalSkipped
      val icon = if (totalFailures == 0 && totalErrors == 0) "✅" else "❌"
      val details = mutableListOf<String>()
      if (totalFailures > 0) details.add("$totalFailures failed")
      if (totalErrors > 0) details.add("$totalErrors errors")
      if (totalSkipped > 0) details.add("$totalSkipped skipped")
      val extra = if (details.isNotEmpty()) " (${details.joinToString()})" else ""
      log("$icon Unit Tests:    $passed/$totalTests passed$extra")
    } else {
      log("⚪ Unit Tests:    Not run (run: ./gradlew testDebugUnitTest)")
    }

    // 3. JaCoCo Coverage (Changed Code & Whole Project)
    val jacocoXml = File(root, "reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    if (jacocoXml.exists()) {
      val content = jacocoXml.readText()

      // A. Coverage for changed Kotlin / Java source files in current commit
      val changedSourceFiles = gitChanges
        .map { it.trim().split(Regex("""\s+""")).last() }
        .filter { it.endsWith(".kt") || it.endsWith(".java") }
        .map { File(it).name }

      val changedCoverage = mutableListOf<String>()
      changedSourceFiles.forEach { fileName ->
        val escapedName = Regex.escape(fileName)
        val fileBlock = Regex("""<sourcefile name="$escapedName">(.*?)</sourcefile>""", RegexOption.DOT_MATCHES_ALL)
          .find(content)?.groupValues?.get(1)
        if (fileBlock != null) {
          val instMatch = Regex("""<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"/>""").findAll(fileBlock).lastOrNull()
          val lineMatch = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""").findAll(fileBlock).lastOrNull()
          if (instMatch != null) {
            val m = instMatch.groupValues[1].toDouble()
            val c = instMatch.groupValues[2].toDouble()
            val instPct = if (m + c > 0) (c * 100.0 / (m + c)) else 0.0
            val linePct = lineMatch?.let {
              val lm = it.groupValues[1].toDouble()
              val lc = it.groupValues[2].toDouble()
              if (lm + lc > 0) (lc * 100.0 / (lm + lc)) else 0.0
            } ?: 0.0
            changedCoverage.add(String.format("%s: %.1f%% lines", fileName, linePct))
          }
        }
      }

      if (changedCoverage.isNotEmpty()) {
        log("🎯 Changed Code Cov:  " + changedCoverage.joinToString(", "))
      } else if (changedSourceFiles.isNotEmpty()) {
        log("🎯 Changed Code Cov:  Excluded from coverage filter (UI/Service)")
      }

      // B. Overall project coverage
      val instMatch = Regex("""<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"/>""").findAll(content).lastOrNull()
      val lineMatch = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""").findAll(content).lastOrNull()

      if (instMatch != null) {
        val missedInst = instMatch.groupValues[1].toLong()
        val covInst = instMatch.groupValues[2].toLong()
        val totalInst = missedInst + covInst
        val instPct = if (totalInst > 0) (covInst * 100.0 / totalInst) else 0.0

        val linePct = lineMatch?.let {
          val m = it.groupValues[1].toLong()
          val c = it.groupValues[2].toLong()
          if (m + c > 0) (c * 100.0 / (m + c)) else 0.0
        } ?: 0.0

        log(String.format("📈 Project Coverage:  %.1f%% instructions (%.1f%% lines)", instPct, linePct))
      }
    } else {
      log("⚪ Test Coverage:     Not run (run: ./gradlew jacocoTestReport)")
    }

    // 4. Android Lint
    val lintTxt = File(root, "reports/lint-results-debug.txt")
    if (lintTxt.exists()) {
      val text = lintTxt.readText().trim()
      val summaryLine = if (text.contains("No issues found")) {
        "0 errors, 0 warnings (clean)"
      } else {
        lintTxt.readLines().lastOrNull { it.contains("errors") && it.contains("warnings") } ?: text.lines().firstOrNull() ?: "Completed"
      }
      val icon = if (summaryLine.startsWith("0 errors")) "✅" else "❌"
      log("$icon Android Lint:  $summaryLine")
    } else {
      log("⚪ Android Lint:  Not run (run: ./gradlew lintDebug)")
    }

    log("=".repeat(60) + "\n")

    reportFile.get().asFile.apply {
      parentFile?.mkdirs()
      writeText(out.toString())
    }
  }
}

tasks.register<QualitySummaryTask>("qualitySummary") {
  group = "reporting"
  description = "Prints a concise summary of current commit changes, unit tests, coverage, and lint."
  buildDir.set(layout.buildDirectory)
  rootDir.set(rootProject.layout.projectDirectory)
  reportFile.set(layout.buildDirectory.file("reports/quality-summary.txt"))
}

tasks.withType<Test>().configureEach {
  maxHeapSize = "2048m"

  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showStandardStreams = false
  }

  systemProperty("java.awt.headless", "true")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    jvmArgs(
      "--enable-native-access=ALL-UNNAMED",
      "-XX:+IgnoreUnrecognizedVMOptions",
      "--sun-misc-unsafe-memory-access=allow"
    )
  }
}



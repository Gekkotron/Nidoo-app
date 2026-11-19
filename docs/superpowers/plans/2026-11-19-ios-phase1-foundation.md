# iOS Phase 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The KMP logic modules compile and test on iOS, and a Compose Multiplatform "Nidoo" screen built from `:shared` runs on the user's iPhone, with iOS checked in CI.

**Architecture:** The two KMP convention plugins gain `iosArm64` + `iosSimulatorArm64`. A new `nidoo.kmp.compose` convention plugin adds Compose Multiplatform. `:shared` becomes the iOS umbrella and exports a static `Nidoo` framework with `MainViewController()`. A thin XcodeGen-described `iosApp/` hosts it. Android behaviour does not change.

**Tech Stack:** Kotlin 2.4.20, AGP 9.5 KMP library plugin, Compose Multiplatform (latest stable compatible with Kotlin 2.4.20), XcodeGen, Xcode 27.2, GitHub Actions macOS runner.

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (Phase 1 in §7.6; constraints in §5; CI call in §9).

## Global Constraints

- iOS targets: `iosArm64` and `iosSimulatorArm64` only (no `iosX64`).
- **Never boot the iOS Simulator locally without the user's explicit yes.** That includes `*IosSimulatorArm64Test` tasks and `xcodebuild test`. Local verification uses compile/link tasks and `xcodebuild build` for the `iphonesimulator` SDK with a `generic/platform=iOS Simulator` destination, which builds without booting anything. iOS tests run in CI.
- No Docker on the Mac. Never add per-repo overrides of `$DEV_SCRATCH` cache variables.
- Android must stay green and unchanged: the existing CI task lists keep passing and `:app:assembleDebug` succeeds after every task.
- Attribution is Gekkotron everywhere (Xcode `ORGANIZATION_NAME`, copyright). Commits use `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …` and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Bundle id `com.gekkotron.nidoo`, display name `Nidoo`. The signing team and any bundle id override live only in an untracked local xcconfig.
- Use the app name Nidoo. The CI brand check rejects the old name outside `docs/superpowers/`.

## Review Focus

1. **Native-only test failures in commonTest.** Number/string formatting, `Regex` and float behaviour can differ on Kotlin/Native, for example in the Jinja-subset templates and `ColorMath`. Expected: every existing `commonTest` passes on `iosSimulatorArm64` exactly as on the JVM. Owner: Task 4, where the CI job runs them. Task 1 compiles them.
2. **CMP pulling Android Compose to different versions.** `:app` depends on `:shared`, so CMP's Android artifacts join the app's classpath. Expected: `androidx.compose.ui:ui` and `material3` resolve to the same versions as before Task 2 on `:app:debugRuntimeClasspath`. Owner: Task 2.
3. **Linux CI jobs with iOS targets declared.** Expected: the existing Ubuntu jobs configure and run as before, with no Kotlin/Native failure or download on Linux. Owner: Task 1 (`kotlin.native.ignoreDisabledTargets=true`), checked by Task 4's CI run.
4. **Xcode's build phase with no Java in its environment.** Xcode script phases don't load `~/.zshrc`. Expected: building from Xcode finds JDK 17 on its own, and a Gradle failure fails the Xcode build with the Gradle error visible. Owner: Task 3.
5. **Free Apple ID refusing the bundle id.** Expected: the user can set another bundle id and their team in `iosApp/Configuration/Local.xcconfig` without touching tracked files. Owner: Task 3.

---

### Task 1: iOS targets on the logic modules

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/nidoo.kmp.jvm.library.gradle.kts`: add `iosArm64()` and `iosSimulatorArm64()` (module build files keep their own `commonTest` dependencies). Replace the "land in Phase 6" comment with a line saying iOS targets are declared here.
- Modify: `gradle.properties`: add `kotlin.native.ignoreDisabledTargets=true`, with a comment explaining that Linux CI can't build Apple targets.
- Modify as needed: any `core/{model,state,source,history,dashboards,flows,climate,assist}/src/commonMain|commonTest` file that fails to compile for Kotlin/Native. No such file is known, since a grep found no `java.*` imports or JVM-only calls. Fix by using the stdlib equivalent, not by moving code to `jvmMain`.

**Interfaces:**
- Produces: every `nidoo.kmp.jvm.library` module has `iosArm64` and `iosSimulatorArm64` compilations of `commonMain`. `jvmMain` stays JVM-only and isn't visible on iOS, which is expected until Phase 2.

- [ ] **Step 1: Confirm the baseline is green.** Run `./gradlew :core:model:jvmTest :core:state:jvmTest :core:source:jvmTest :core:history:jvmTest :core:dashboards:jvmTest :core:flows:jvmTest :core:climate:jvmTest :core:assist:jvmTest`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2: Add the iOS targets and the gradle property.**
- [ ] **Step 3: Compile main and test code for iOS without running anything.** Run `./gradlew` with `compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64` and `linkDebugTestIosSimulatorArm64`, for each of the eight modules: `:core:model`, `:core:state`, `:core:source`, `:core:history`, `:core:dashboards`, `:core:flows`, `:core:climate`, `:core:assist`. For example: `:core:model:compileKotlinIosArm64 :core:model:compileKotlinIosSimulatorArm64 :core:model:linkDebugTestIosSimulatorArm64`. Expected: BUILD SUCCESSFUL. If something fails, fix that file (see Files) and rerun.
- [ ] **Step 4: Confirm Android and the JVM didn't move.** Rerun Step 1's command, then `./gradlew :app:assembleDebug`. Expected: both BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** with `feat(build): compile the KMP logic modules for iOS`.

### Task 2: Compose Multiplatform in `:shared` and the iOS framework

**Files:**
- Modify: `gradle/libs.versions.toml`, adding:
  - version `composeMultiplatform`: the latest stable release whose compatibility table lists Kotlin 2.4.20;
  - library `compose-multiplatform-gradle-plugin = org.jetbrains.compose:compose-gradle-plugin`;
  - libraries `cmp-runtime`, `cmp-foundation`, `cmp-material3`, `cmp-ui`, using the `org.jetbrains.compose.*` coordinates that release documents. If material3 is versioned separately in that release, give it its own version entry.
- Modify: `build-logic/convention/build.gradle.kts`: add `implementation(libs.compose.multiplatform.gradle.plugin)`.
- Modify: `build-logic/convention/src/main/kotlin/nidoo.kmp.library.gradle.kts`: add `iosArm64()` and `iosSimulatorArm64()`, enable the AGP KMP Android host test so `commonTest` runs on the JVM locally, and replace the "land in Phase 6" comment.
- Create: `build-logic/convention/src/main/kotlin/nidoo.kmp.compose.gradle.kts`. It applies `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose`, and adds `cmp-runtime`, `cmp-foundation`, `cmp-material3` and `cmp-ui` to `commonMain`. Apply it after `nidoo.kmp.library`.
- Modify: `shared/build.gradle.kts`:
  - apply `nidoo.kmp.compose`;
  - add `commonMain` → `implementation(project(":core:model"))`;
  - for every `KotlinNativeTarget`, add `binaries.framework { baseName = "Nidoo"; isStatic = true }`.
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/SharedCoreSummary.kt`, defining `fun sharedCoreSummary(): String`.
- Create: `shared/src/commonMain/kotlin/com/gekkotron/nidoo/shared/NidooRoot.kt`, defining `@Composable fun NidooRoot()`: a centred "Nidoo" title above `sharedCoreSummary()`, on a dark background. It's a placeholder, so there's no design-system dependency.
- Create: `shared/src/iosMain/kotlin/com/gekkotron/nidoo/shared/MainViewController.kt`, defining `fun MainViewController(): UIViewController = ComposeUIViewController { NidooRoot() }`.
- Test: `shared/src/commonTest/kotlin/com/gekkotron/nidoo/shared/SharedCoreSummaryTest.kt`.
- Keep `NidooShared.VERSION` untouched: `:app` references it.

**Interfaces:**
- Consumes: Task 1's iOS compilations of `:core:model` (`com.gekkotron.nidoo.model.Kind`).
- Produces: the Swift-visible `MainViewControllerKt.MainViewController()` in framework `Nidoo`, and the Gradle task `:shared:embedAndSignAppleFrameworkForXcode`.

- [ ] **Step 1: Record the Android Compose versions as they are now.** Run `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.compose.ui:ui` and the same for `androidx.compose.material3:material3`. Save the resolved versions in the task notes.
- [ ] **Step 2: Write the failing test.**

```kotlin
class SharedCoreSummaryTest {
    @Test fun countsDeviceKindsFromTheSharedCore() {
        assertEquals("13 device kinds from the shared core", sharedCoreSummary())
    }
}
```

- [ ] **Step 3: Run it and watch it fail.** Use the `:shared` Android host test task that the convention change in Files enables (`./gradlew :shared:tasks --all | grep -i hosttest` gives its name). Expected: compilation fails with an unresolved `sharedCoreSummary`.
- [ ] **Step 4: Implement it, plus the convention plugin, `NidooRoot` and `MainViewController`.** `sharedCoreSummary()` returns `"${Kind.entries.size} device kinds from the shared core"`.
- [ ] **Step 5: Run the test again.** Expected: PASS.
- [ ] **Step 6: Build the iOS framework twice.** Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64` twice. Expected: both runs BUILD SUCCESSFUL, and the second one reports "Reusing configuration cache."
- [ ] **Step 7: Check Android.** Run `./gradlew :app:assembleDebug :app:test`, then repeat Step 1's two `dependencyInsight` commands. Expected: everything passes, and the Compose versions match Step 1. If they don't, constrain CMP's Android artifacts to the BOM versions in `nidoo.kmp.compose` before going on.
- [ ] **Step 8: Commit** with `feat(shared): Compose Multiplatform root and the Nidoo iOS framework`.

### Task 3: The `iosApp` Xcode shell

**Files:**
- Create: `iosApp/project.yml` (XcodeGen), defining:
  - target `Nidoo`, application, platform iOS, deployment target `17.0`;
  - `ORGANIZATION_NAME: Gekkotron`, `PRODUCT_BUNDLE_IDENTIFIER: com.gekkotron.nidoo`, `CODE_SIGN_STYLE: Automatic`;
  - configs from `Configuration/Config.xcconfig`;
  - a pre-build script phase running `"$SRCROOT/Scripts/build-shared.sh"`;
  - `OTHER_LDFLAGS` including `-framework Nidoo`, with the framework search path set to Gradle's output for `$(SDK_NAME)` per the CMP Xcode integration docs.
- Create: `iosApp/Configuration/Config.xcconfig`, holding the shared settings and ending in `#include? "Local.xcconfig"`.
- Create: `iosApp/Configuration/Local.xcconfig.example`, a template with `DEVELOPMENT_TEAM =` and a commented-out `PRODUCT_BUNDLE_IDENTIFIER = com.gekkotron.nidoo.<something>`.
- Create: `iosApp/Scripts/build-shared.sh`. It's executable and starts with `set -euo pipefail`. If `JAVA_HOME` is empty, it sets it with `/usr/libexec/java_home -v 17`. Then it runs `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so Gradle's exit code fails the Xcode build.
- Create: `iosApp/Nidoo/NidooApp.swift`, a SwiftUI `@main App` showing `ComposeView().ignoresSafeArea()`.
- Create: `iosApp/Nidoo/ComposeView.swift`, a `UIViewControllerRepresentable` returning `MainViewControllerKt.MainViewController()`.
- Create: `iosApp/Nidoo/Info.plist`, with `CFBundleDisplayName` set to `Nidoo` and `CADisableMinimumFrameDurationOnPhone` set to `true`. No network, camera or microphone keys yet: they arrive with the phases that need them (spec §5).
- Create: `iosApp/Nidoo/Assets.xcassets`, holding an `AppIcon` built from the existing Android launcher artwork (1024 px).
- Modify: `.gitignore`, adding `iosApp/*.xcodeproj/`, `iosApp/Configuration/Local.xcconfig` and Xcode user data (`xcuserdata/`, `*.xcuserstate`, `DerivedData/`).

**Interfaces:**
- Consumes: Task 2's `MainViewControllerKt.MainViewController()` and `:shared:embedAndSignAppleFrameworkForXcode`.
- Produces: `xcodegen generate --spec iosApp/project.yml`, which creates `iosApp/Nidoo.xcodeproj` with scheme `Nidoo`.

- [ ] **Step 1: Install XcodeGen** if it's missing: `brew install xcodegen`. Expected: `xcodegen --version` prints a version.
- [ ] **Step 2: Create the files and generate the project.** Run `xcodegen generate --spec iosApp/project.yml`. Expected: it writes `iosApp/Nidoo.xcodeproj`, and `git status` doesn't list it.
- [ ] **Step 3: Build for the Simulator SDK without booting a Simulator.** Run this with `env -i HOME="$HOME" PATH="/usr/bin:/bin:/usr/sbin:/sbin"`, so there's no `JAVA_HOME`, which is Review Focus 4: `xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`. Expected: `** BUILD SUCCEEDED **`.
- [ ] **Step 4: Check that a Gradle failure stops Xcode.** Add a deliberate syntax error to `SharedCoreSummary.kt` and rerun Step 3. Expected: `** BUILD FAILED **`, with the Kotlin compiler error in the log. Then revert the error.
- [ ] **Step 5: Commit** with `feat(ios): Xcode shell hosting the shared Compose root`.

### Task 4: iOS in CI

**Files:**
- Modify: `.github/workflows/ci.yml`, adding a job `ios` (`name: iOS build and tests`) on `macos-latest` with `timeout-minutes: 40`. Its steps:
  1. checkout, `setup-java` 17 temurin and `gradle/actions/setup-gradle@v5`, as the existing jobs use;
  2. `./gradlew -Dorg.gradle.configuration-cache=false --stacktrace --continue` with these tasks:
     - `iosSimulatorArm64Test` for `:core:model`, `:core:state`, `:core:source`, `:core:history`, `:core:dashboards`, `:core:flows`, `:core:climate`, `:core:assist` and `:shared`;
     - `:shared:linkDebugFrameworkIosArm64`;
  3. `brew install xcodegen`, then `xcodegen generate --spec iosApp/project.yml`;
  4. Task 3 Step 3's `xcodebuild … CODE_SIGNING_ALLOWED=NO build`.
  - Leave the existing `test` and `brand` jobs unchanged.

**Interfaces:**
- Consumes: the Gradle tasks from Tasks 1 and 2, and the XcodeGen spec from Task 3.

- [ ] **Step 1: Add the job and check the YAML.** Run `ruby -ryaml -e 'YAML.load_file(".github/workflows/ci.yml")'` (Ruby ships with macOS). Expected: no output, exit 0.
- [ ] **Step 2: Commit** with `ci: build and test the iOS targets on macOS`.
- [ ] **Step 3: Push the branch and watch CI.** Run `GH_TOKEN=$(gh auth token --user Gekkotron) gh run watch --repo Gekkotron/Nidoo-app`. Expected: all four jobs pass (`core`, `feature`, `app`, `ios`) along with `brand`. If an iOS `commonTest` fails (Review Focus 1), fix the code so it behaves the same on both platforms. Change the code, not the test's expected value, unless the JVM value was itself wrong. Then push again.

### Task 5: Run on your iPhone, and the SideStore notes

**Files:**
- Modify: `README.md`, adding a section `## Run on your iPhone` after `## Try the demo`. It covers:
  1. prerequisites (Xcode, `brew install xcodegen`, a free Apple ID added in Xcode → Settings → Accounts);
  2. `cp iosApp/Configuration/Local.xcconfig.example iosApp/Configuration/Local.xcconfig`, then filling in `DEVELOPMENT_TEAM`, plus the bundle id override if Xcode says the id is taken;
  3. `xcodegen generate --spec iosApp/project.yml && open iosApp/Nidoo.xcodeproj`;
  4. on the phone: Developer Mode (Settings → Privacy & Security), connect over USB, Run, then trust the developer profile under Settings → General → VPN & Device Management;
  5. **Keeping it past 7 days:** install SideStore (pairing file generated once from the Mac, LocalDevVPN on the phone), add Nidoo's `.ipa`, and let SideStore refresh it. Note the free-account limits: 3 sideloaded apps including SideStore, no push notifications. Producing the `.ipa`: Xcode → Product → Archive → Distribute App → Custom → Debugging (or Release Testing). Check against SideStore's current docs while writing.
  - Also add a `iosApp/` row to the Modules table, and replace "Phase 6" mentions elsewhere if any remain.

- [ ] **Step 1: Write the section.** Check that every command in it matches Tasks 3 and 4 exactly.
- [ ] **Step 2: Run the brand check locally.** Run `git grep -n -i 'oi[k]os' -- . ':!docs/superpowers'`. Expected: no output.
- [ ] **Step 3: Commit** with `docs(readme): run Nidoo on an iPhone`.
- [ ] **Step 4: Hand over to the user for the device install.** This is the user's action, with the phone connected: follow the README section and confirm the "Nidoo / 13 device kinds from the shared core" screen opens on the iPhone. The phase is done when they confirm.

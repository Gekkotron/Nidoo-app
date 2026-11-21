# iOS Phase 2 (Platform Layer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crypto, transport (direct MQTT, gateway, LAN HTTP), data storage, the logic modules' `jvmMain` code and demo mode all compile and pass their tests on iOS. Android behaves exactly as before. The iOS placeholder screen proves it by showing the demo house, read through the shared transport and discovery code.

**Architecture:** Each module converts in place, one PR per module (spec §9). Code that runs the same on both platforms goes to `commonMain`. Android keeps its proven implementations in `jvmMain`/`androidMain` wherever "Android unchanged" would otherwise rest on a rewrite: HiveMQ, `javax.crypto`, Keystore and `TcpProbe`. iOS gets new implementations behind the existing interfaces. HTTP moves to Ktor in `commonMain`. On Android, Ktor's OkHttp engine wraps the app's existing `OkHttpClient`. The gateway SDK becomes multiplatform in its own repo and is published to a static Maven repo on GitHub Pages.

**Tech Stack:** Kotlin 2.4.20, AGP 9.5 KMP library plugin, Ktor client 3.x (OkHttp engine on Android, Darwin on iOS), ktor-network, cryptography-kotlin (CryptoKit provider, iOS only), AndroidX DataStore `datastore-core-okio` + Okio, an iOS MQTT client chosen in Task 6, GitHub Actions (macOS runner, Mosquitto from Homebrew).

**Spec:** `docs/superpowers/specs/2026-11-19-ios-compose-multiplatform-design.md` (§7.2 platform layer, §7.6 phase 2, §8 testing, §9 calls, §10 gateway client). Phase 1 conventions: `docs/superpowers/plans/2026-11-19-ios-phase1-foundation.md`.

## Calls this plan makes (the spec leaves them to the builder; each can be overturned)

1. **Android keeps its current crypto, MQTT, Keystore and TCP probe code.** These only move source sets; iOS gets its own implementations. Only HTTP goes through a shared rewrite (Ktor), because the spec asks for it and the existing MockWebServer tests guard it.
2. **Gateway SDK distribution: a static Maven repo on GitHub Pages** (`https://gekkotron.github.io/Nidoo-gateway/maven`), published by the gateway repo's CI on a macOS runner when a `v2.*` tag is pushed. Consumers need no credentials, and published versions are never overwritten, which keeps Nidoo's CI reproducible. Rejected: GitHub Packages (needs a token even to read public packages, which breaks local builds), Maven Central (needs a Sonatype account and GPG setup on the user's side), and a submodule or included build (ties the two builds' Gradle and Kotlin versions together). JitPack keeps serving the `v1.x` JVM tags.
3. **New SDK coordinates:** `io.github.gekkotron:athena-gateway-client:2.0.0`. This matches the SDK's package, and the move to multiplatform is a new major anyway. Existing JVM call sites stay source-compatible: the `OkHttpClient` constructor stays in `jvmMain`.
4. **The iOS MQTT client goes behind a callback-style `MqttClientPort`.** Swift can implement a callback interface but not a Kotlin `suspend` one, so the same interface serves both a Kotlin MQTT library and the spec §9 Swift fallback (CocoaMQTT). Task 6 starts with a pass/fail gate that picks one of the two.
5. **The gateway client never follows redirects on iOS.** On Android it keeps today's `followSslRedirects(false)`. The gateway has no reason to redirect, and this rules out any https→http downgrade.
6. **Not in this plan:** Room/alerts (Phase 6), Coil (lands with the screens), Koin (Phase 3), wiring the iOS network and foreground signals into a `TransportSelector` (the Phase 4 app shell).

## Global Constraints

- iOS targets are `iosArm64` and `iosSimulatorArm64` only.
- **Never boot the iOS Simulator locally without the user's explicit yes.** That rules out `*IosSimulatorArm64Test` tasks and `xcodebuild test`. Local iOS verification means `compileKotlinIosArm64` + `linkDebugTestIosSimulatorArm64` per module. iOS tests run in CI.
- No Docker on the Mac. No per-repo overrides of the `$DEV_SCRATCH` cache variables.
- **Android must not regress** (spec §4). Every test that exists today keeps passing, and no test is deleted or weakened to make a move compile. Each task records its module's test count before the move and checks after it that the JVM/Android count plus the new `commonTest` count is at least as large. `./gradlew :app:assembleDebug` succeeds after every task.
- **Existing Android installs upgrade in place** (spec §9): DataStore file names and locations, serialized formats and Keystore aliases stay as they are.
- No secret is ever logged. The AES-256-GCM envelope stays byte-compatible with the gateway.
- Versions are pinned in `gradle/libs.versions.toml`. For each new library, use the latest stable release whose docs or POM state compatibility with Kotlin 2.4.20.
- `java.*`/`javax.*` → common replacements in moved code:
  - `java.io.IOException` → `kotlinx.io.IOException` (a typealias on the JVM, so existing `catch` sites keep working);
  - `java.util.Base64` → `kotlin.io.encoding.Base64.Default.withPadding(PaddingOption.PRESENT_OPTIONAL)`;
  - `System.currentTimeMillis`/`java.time.Instant` → `kotlin.time.Clock`/`kotlin.time.Instant`;
  - `ZoneId` → `kotlinx-datetime` `TimeZone`;
  - `UUID` → `kotlin.uuid.Uuid`;
  - `ConcurrentHashMap`/`Atomic*` → `kotlin.concurrent.atomics` or a `Mutex`-guarded value;
  - `Dispatchers.IO` → the common `kotlinx.coroutines.IO`;
  - `"%02x".format` → `toHexString()`.
- CI: when a module goes multiplatform, its Linux CI entry is renamed to match (`:X:test` → `:X:jvmTest`, or `:X:testAndroidHostTest` for `nidoo.kmp.library`; check with `./gradlew :X:tasks`), and `:X:iosSimulatorArm64Test` is added to the macOS "iOS build and tests" job, all in the same task.
- Attribution: Gekkotron everywhere. Commits use `git -c user.name=Gekkotron -c user.email=60887050+Gekkotron@users.noreply.github.com commit …` and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. The app is named Nidoo (the CI brand check enforces this).
- Gateway repo work (Tasks 2–3) happens on a branch in `~/superconductor/projects/Nidoo-gateway`. Opening its PR is fine. Merging it, pushing the `v2.0.0` tag and enabling Pages need the user's yes.

## Review Focus

1. **Base64 that today's decoder accepts.** `java.util.Base64.getDecoder()` takes input with or without `=` padding. Kotlin's `Base64.Default` rejects unpadded input unless told otherwise. Expected: every envelope the JVM opens today also opens on iOS, padded or not. Owner: Task 1 (`opensUnpaddedEnvelope`).
2. **An upgraded Android install reads its old files.** Expected: preferences, profiles (including encrypted secrets) and the entity cache written by the current `DataStoreFactory`/`Serializer` code are read unchanged by the Okio-based code. Owner: Task 7 (`LegacyFilesTest`, using files captured before the change).
3. **Darwin network errors must be retried, not turned into refusals.** When iOS resumes the app, NSURLSession fails the open gateway stream with an `NSURLErrorDomain` error wrapped in Ktor's Darwin exception. Expected: the SDK maps it to `GatewayException.Transport`, which `GatewayTransport` retries. It must never end up as a `Refusal` that sends the user to settings. Owner: Task 2 (`engineFailureIsTransport`).
4. **A websocket profile (`TransportSpec.Direct.ws = true`) on iOS.** Expected: it connects over `ws(s)://host:port/mqtt` as it does on Android, or, if the chosen client can't, the link shows `Offline` with a readable reason instead of retrying forever. Owner: Task 6 (`wsProfileUsesMqttPath` / `unsupportedWsIsOffline`).
5. **Keychain locked (before first unlock, or a background launch).** Expected: `KeychainSecretStore` throws `SecretUnavailable`, which callers already handle, and never crashes or creates a second key that would orphan the saved secrets. Owner: Task 8 (`lockedKeychainIsUnavailable`, `existingKeyIsNeverReplaced`).

---

### Task 1: `:core:crypto` on iOS

**Files:**
- Modify: `core/crypto/build.gradle.kts`: `nidoo.kmp.jvm.library` + `org.jetbrains.kotlin.plugin.serialization`. `commonMain` gets `api(kotlinx-serialization-json)`. `iosMain` gets `implementation(cryptography-core, cryptography-provider-cryptokit)`.
- Modify: `build-logic/convention/src/main/kotlin/nidoo.kmp.jvm.library.gradle.kts`: add `kotlinx-coroutines-test` to `commonTest` (later tasks move coroutine tests there).
- Move: `src/main/kotlin/**` → `src/commonMain/kotlin/**`; `src/test/kotlin/**` → `src/commonTest/kotlin/**`.
- Create: `src/commonMain/kotlin/com/gekkotron/nidoo/crypto/AesGcm256.kt` (`expect object AesGcm256`), `src/jvmMain/.../AesGcm256.jvm.kt` (`javax.crypto` code moved verbatim from `EnvelopeCrypto`), `src/iosMain/.../AesGcm256.ios.kt` (CryptoKit provider, using its explicit-IV encrypt/decrypt, which is `@DelicateCryptographyApi`).
- Create: `src/commonMain/.../SecureRandom.kt` (`internal expect fun secureRandomBytes(size: Int): ByteArray`) with a JVM actual (`SecureRandom`) and an iOS actual (`SecRandomCopyBytes`; throws `IllegalStateException` on a non-zero status).
- Test: `src/commonTest/.../EnvelopeVectorsTest.kt`.
- Modify: `.github/workflows/ci.yml` (see Global Constraints).

**Interfaces:**
- Produces: `expect object AesGcm256 { fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray /* ciphertext‖tag[16] */; fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray /* throws EnvelopeException.AuthenticationFailed */ }`, plus `fun AesGcm256.sealWithRandomNonce(key: ByteArray, plaintext: ByteArray): ByteArray` (returns nonce[12]‖ciphertext‖tag) and `fun AesGcm256.openNonceFirst(key: ByteArray, data: ByteArray): ByteArray`, and `fun AesGcm256.newKey(): ByteArray` (32 bytes from `secureRandomBytes`). Task 8 uses the last three.
- `EnvelopeCrypto`'s public API, defaults and output bytes are unchanged.

- [ ] **Step 1: Capture vectors from today's implementation.** Before moving anything, add a throwaway JVM test that prints `sealBytes` output for key `000102…1f`, nonce `a0a1…ab` and plaintexts `{"timestamp":1700000000,"cmd":"on"}` and `""`. Paste the two base64 strings into `EnvelopeVectorsTest` as constants, then delete the throwaway test.
- [ ] **Step 2: Write `EnvelopeVectorsTest`** (`sealMatchesJvmVector`, `opensJvmVector`, `opensUnpaddedEnvelope` (the vector with its `=` stripped), `tamperedTagFailsAuthentication` (expects `EnvelopeException.AuthenticationFailed`), `nonceIsTwelveRandomBytes` (two seals differ; decoded length = 12 + plaintext + 16)). Run `./gradlew :core:crypto:test`. Expected: PASS on the old layout for everything except `opensUnpaddedEnvelope`, which may fail. That failure is the Review Focus #1 bug being pinned down.
- [ ] **Step 3: Convert the module** as listed in Files. Then rerun Step 2 as `:core:crypto:jvmTest`, plus `:core:crypto:compileKotlinIosArm64 :core:crypto:linkDebugTestIosSimulatorArm64 :core:transport:test`. Expected: all PASS. The test count is at least the old count plus the new tests.
- [ ] **Step 4: Commit:** `feat(crypto): AES-256-GCM envelope on iOS`. PR 1.

### Task 2: Gateway SDK on Kotlin Multiplatform (repo `Nidoo-gateway`, `sdk/kotlin`)

**Files:**
- Modify: `sdk/kotlin/build.gradle.kts`: switch to `kotlin("multiplatform")` 2.4.20 with `jvm()` (JVM 11 bytecode kept), `iosArm64()`, `iosSimulatorArm64()` and `explicitApi()`. `commonMain` gets `api` on ktor-client-core, coroutines and serialization-json, plus `implementation` on cryptography-core. `jvmMain` gets `api(okhttp 4.12.0)` and `implementation(ktor-client-okhttp)`. `iosMain` gets `implementation(ktor-client-darwin, cryptography-provider-cryptokit)`. Group becomes `io.github.gekkotron`; artifactId stays `athena-gateway-client`. Kover (JVM, min 85) is kept.
- Move to `commonMain`: `Models.kt`, `GatewayException.kt` (`Transport`'s cause type becomes `Throwable`), `AthenaGatewayClient.kt` (its common constructor takes `HttpClient`), `internal/RequestBuilder.kt`, `internal/ResponseDecoder.kt`, and `internal/WireCrypto.kt` (AES-GCM through an `internal expect object` like Task 1's; okio base64 is replaced with `kotlin.io.encoding.Base64`).
- Replace `internal/Transport.kt`'s `OkHttpTransport` with `internal class KtorTransport(baseUrl: String, client: HttpClient) : Transport`. Streams: `Accept: text/event-stream`, `HttpTimeout` socket 45 s and no request timeout, lines read from `bodyAsChannel()`, a `data:` line becomes `StreamItem.Data`, and a non-event-stream reply becomes `StreamItem.NotAStream`. `post(readTimeoutSeconds)` raises the socket timeout and never lowers it. `followRedirects = false`. Any engine exception other than cancellation becomes `GatewayException.Transport`.
- Create: `jvmMain/.../AthenaGatewayClientJvm.kt`: `public fun AthenaGatewayClient(baseUrl: String, secretKey: String, client: OkHttpClient = OkHttpClient()): AthenaGatewayClient`, using the OkHttp engine with `preconfigured = client` (same signature as today's constructor).
- Create: `iosMain/.../AthenaGatewayClientIos.kt`: `public fun AthenaGatewayClient(baseUrl: String, secretKey: String): AthenaGatewayClient`, using the Darwin engine.
- Tests: `RequestBuilderTest`, `ResponseDecoderTest`, `ModelsTest`, `WireCryptoTest`, `SubscribeTest`, `AthenaGatewayClientTest` and `FakeTransport` move to `commonTest`. `OkHttpTransportTest` becomes `jvmTest/KtorOkHttpTransportTest` (MockWebServer, same cases). `GatewayIntegrationTest` stays in `jvmTest` with its tag.
- Create: `commonTest/.../KtorTransportTest.kt` (Ktor `MockEngine`).

**Interfaces:**
- Produces, for Task 5, the public API unchanged apart from the constructors above: `http`, `download`, `publish`, `subscribe`, `MqttEvent`, `GatewayException.*`, `Broker`, `GatewayResponse`, `GatewayDownload`.

- [ ] **Step 1: Baseline.** In `sdk/kotlin`, run `./gradlew test` and record the test count. Generate a wire vector with the server's own Python crypto (key `000102…1f`, nonce `a0a1…ab`, JSON `{"ok":true}`) and add it to `WireCryptoTest` as `opensServerVector`. Run it. Expected: PASS on the JVM as-is.
- [ ] **Step 2: Write `KtorTransportTest`:**
  - `sseDataLinesBecomeItems`: two `data:` lines plus a comment line give two `Data` items;
  - `nonStreamReplyIsNotAStream`: 403 JSON gives `NotAStream(status = 403)`;
  - `engineFailureIsTransport`: `MockEngine` throws `IllegalStateException("NSURLErrorDomain -1005")` and `post` throws `GatewayException.Transport`;
  - `redirectIsNotFollowed`: a 302 to `http://…` gives a `RawResponse` with status 302 and the engine sees exactly 1 request.
- [ ] **Step 3: Convert the build and sources** as listed in Files. Run `./gradlew jvmTest compileKotlinIosArm64 linkDebugTestIosSimulatorArm64 koverVerify`. Expected: PASS, and the JVM test count is at least the Step 1 count.
- [ ] **Step 4: Commit:** `feat(sdk): Kotlin Multiplatform client on Ktor (JVM + iOS)`.

### Task 3: SDK CI on macOS and Pages publishing (repo `Nidoo-gateway`)

**Files:**
- Modify: `.github/workflows/ci.yml`: add a `sdk-ios` job on `macos-latest` that runs `./gradlew allTests` in `sdk/kotlin`.
- Modify: `sdk/kotlin/build.gradle.kts`: add `publishing.repositories.maven { name = "pages"; url = uri(layout.buildDirectory.dir("pages-repo")) }`.
- Create: `.github/workflows/sdk-release.yml`. On a `v2.*` tag push, on `macos-latest`: publish to `pages` with `-Pversion=${TAG#v}`, then check out `gh-pages` into `site/`. **Fail if `site/maven/io/github/gekkotron/athena-gateway-client/<version>` already exists.** Otherwise copy the repo in under `site/maven/`, commit as Gekkotron and push. `permissions: contents: write`.
- Modify: `jitpack.yml` (comment: v1.x only) and the SDK README (v2 coordinates, the repository URL and its `content { includeGroup("io.github.gekkotron") }` filter).

- [ ] **Step 1: Dry run.** Run `./gradlew publishAllPublicationsToPagesRepository -Pversion=2.0.0-local` locally. Expected: `build/pages-repo/io/github/gekkotron/athena-gateway-client{,-jvm,-iosarm64,-iossimulatorarm64}/2.0.0-local/` with `.module` files.
- [ ] **Step 2: Commit, push the branch and open the PR**: `ci(sdk): iOS tests and GitHub Pages Maven publishing`. Expected: both CI jobs green.
- [ ] **Step 3: Ask the user** to approve merging, enabling Pages from `gh-pages` (`gh api -X POST repos/Gekkotron/Nidoo-gateway/pages -f 'source[branch]=gh-pages' -f 'source[path]=/'`), and pushing tag `v2.0.0`. After the release run, check that `curl -sfI https://gekkotron.github.io/Nidoo-gateway/maven/io/github/gekkotron/athena-gateway-client/2.0.0/athena-gateway-client-2.0.0.module` returns 200.

### Task 4: `:core:transport` goes multiplatform (no behaviour change)

**Files:**
- Modify: `core/transport/build.gradle.kts`: `nidoo.kmp.jvm.library`, dropping `java-test-fixtures`. `commonMain` gets `api(kotlinx-coroutines-core, kotlinx-io-core)` (for the common `IOException`). `jvmMain` keeps today's dependencies (SDK `v1.4` from JitPack, HiveMQ). `jvmTest` keeps MockWebServer and `:core:crypto`.
- Move to `commonMain` (they need only coroutines, the stdlib and the common replacements): `ActiveTransport`, `Backoff`, `GatewayApi` (the `GatewayApi`, `GatewayEvent`, `Refusal` and `GatewayRefused` types, plus `toPayloadBytes`/`errorText`), `GatewayStream`, `GatewayTransport`, `Handover`, `ManagedTransport`, `MqttTransport`, `TopicFilter`, `TransportSelector`, `TransportSpec`, the `DirectFailure` type, and the `LocalProbe`, `GatewayDownloader`, `HttpRoutes`/`HttpRoute`/`HttpGetResult`/`HttpPostResult` declarations. `DownloadFailed` extends `kotlinx.io.IOException`.
- Keep in `jvmMain`: `DirectMqttTransport`, `classifyFailure`, `AthenaGatewayApi`, `AthenaGatewayDownloader`, `LanOrGatewayGet`, `TcpProbe`.
- Create module `:core:transport-testing` (`nidoo.kmp.jvm.library`, added to `settings.gradle.kts`). `commonMain` gets `FakeMqttTransport`, `FakeGatewayApi` and `FakeManagedTransport` (same package `com.gekkotron.nidoo.transport.testing`). `jvmMain` gets `EmbeddedBroker` (moquette, HiveMQ as `api`).
- Modify: `core/source`, `core/push`, `app` and `feature/home` build files: replace `testFixtures(project(":core:transport"))` with `project(":core:transport-testing")`.
- Tests: tests of common code that use no JVM-only library move to `commonTest`. The rest go to `jvmTest`.

**Interfaces:**
- Produces: the same public names and signatures as before, now visible from iOS for everything listed under `commonMain`. Later tasks rely on `ManagedTransport`, `LinkState`, `MqttMessage`, `TransportSpec.Direct`, `Backoff`, `DirectFailure`, `PublishFailed`, `LocalProbe`, `GatewayDownloader` and `HttpRoutes` being common.

- [ ] **Step 1: Record the baseline**: test counts of `:core:transport:test`, `:core:source:jvmTest`, `:core:push:test`, `:app:testDebugUnitTest`, `:feature:home:testDebugUnitTest`.
- [ ] **Step 2: Move the files and create `:core:transport-testing`** as listed above.
- [ ] **Step 3: Verify.** Run `./gradlew :core:transport:jvmTest :core:transport:compileKotlinIosArm64 :core:transport:linkDebugTestIosSimulatorArm64 :core:transport-testing:compileKotlinIosArm64`, then Step 1's other tasks and `:app:assembleDebug`. Expected: all PASS, with no test count lower than in Step 1.
- [ ] **Step 4: Commit:** `refactor(transport): multiplatform contract, JVM implementations unchanged`.

### Task 5: Gateway and LAN HTTP in `commonMain` on Ktor

**Files:**
- Modify: `settings.gradle.kts`: add `maven("https://gekkotron.github.io/Nidoo-gateway/maven") { content { includeGroup("io.github.gekkotron") } }`, and remove the JitPack repository once nothing resolves from it.
- Modify: `gradle/libs.versions.toml`: `athena-gateway-client = io.github.gekkotron:athena-gateway-client:2.0.0`, plus `ktor-client-core`, `ktor-client-okhttp`, `ktor-client-darwin`, `ktor-client-mock` (test), `ktor-network`. Keep `mockwebserver` for the JVM tests.
- Modify: `core/transport/build.gradle.kts`: `commonMain` gets `api(athena-gateway-client, ktor-client-core)` and `implementation(ktor-network)`. `jvmMain` gets `implementation(ktor-client-okhttp)`, and `iosMain` gets `implementation(ktor-client-darwin)`.
- Move to `commonMain`, rewritten on `HttpClient`: `AthenaGatewayApi`, `AthenaGatewayDownloader`, `LanOrGatewayGet`.
- Create: `jvmMain/.../OkHttpFactories.kt`, with top-level functions named like today's constructors so every Android call site compiles unchanged: `fun AthenaGatewayApi(spec: TransportSpec.Gateway, http: OkHttpClient = OkHttpClient()): AthenaGatewayApi`, `fun AthenaGatewayDownloader(spec: TransportSpec.Gateway, http: OkHttpClient = OkHttpClient()): AthenaGatewayDownloader`, `fun LanOrGatewayGet(routes: HttpRoutes, http: OkHttpClient, isLanHost: (String) -> Boolean, log: (String) -> Unit, io: CoroutineDispatcher = Dispatchers.IO): LanOrGatewayGet`. They build OkHttp-engine clients with `preconfigured =` the exact `OkHttpClient` builders used today (`noDowngrade()`, the 10 s publish call timeout, the LAN timeouts, `followRedirects(false)`, `retryOnConnectionFailure(false)` for the no-resend client).
- Create: `commonMain/.../NetworkTcpProbe.kt`: `class NetworkTcpProbe(host: String, port: Int, timeout: Duration = 1500.milliseconds, connect: suspend (String, Int) -> Unit = ktorConnect) : LocalProbe`. `false` on timeout or any failure; the socket is always closed. iOS uses it; Android keeps `TcpProbe`.
- Create: `iosMain/.../IosHttpClients.kt`: `fun gatewayHttpClient(): HttpClient` (Darwin, `followRedirects = false`) and `fun lanHttpClients(): LanClients` (Darwin, the same LAN timeouts as Android, `followRedirects = false`, no `HttpRequestRetry`).
- Common constructor shapes: `AthenaGatewayApi(spec: TransportSpec.Gateway, http: HttpClient)`, `AthenaGatewayDownloader(spec: TransportSpec.Gateway, http: HttpClient)`, `LanOrGatewayGet(routes: HttpRoutes, lan: LanClients, isLanHost: (String) -> Boolean, log: (String) -> Unit, io: CoroutineDispatcher = Dispatchers.IO)`, with `class LanClients(val lan: HttpClient, val lanOnce: HttpClient)`.
- Tests: the existing `AthenaGatewayApiTest`, `GatewayDownloaderTest`, `LanOrGatewayGetTest` and `NoDowngradeTest` stay in `jvmTest`, unchanged, going through the OkHttp factories. New `commonTest` files: `LanOrGatewayGetMockEngineTest` and `NetworkTcpProbeTest`.

**Interfaces:**
- Consumes: Task 2's SDK `2.0.0`, Task 4's common contract.
- Produces: `gatewayHttpClient()`, `lanHttpClients()`, `NetworkTcpProbe` for the Phase 4 iOS shell, and common `AthenaGatewayApi`/`AthenaGatewayDownloader`/`LanOrGatewayGet`.

- [ ] **Step 1: Write the new common tests:**
  - `lanGetFallsBackToGatewayWhenProbeFails`;
  - `noResendPostIsSentOnce` (MockEngine throws on the first call; asserts 1 request and `HttpPostResult.Unavailable`);
  - `lanRedirectIsNotFollowed`;
  - `bodyOverCapIsRejected` (same cap as today's `LanOrGatewayGet` constant);
  - `NetworkTcpProbeTest.reachableWhenListening` (ktor-network `bind` on port 0) and `unreachableWhenClosed`;
  - `timesOutAsFalse` (injected `connect` that suspends forever; virtual time 1500 ms).

  Run `./gradlew :core:transport:jvmTest`. Expected: FAIL (types not defined).
- [ ] **Step 2: Implement**, as listed in Files.
- [ ] **Step 3: Verify.** Run `./gradlew :core:transport:jvmTest :core:transport:linkDebugTestIosSimulatorArm64 :core:alerts:test :core:push:test :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS, with every pre-existing transport test still present.
- [ ] **Step 4: Commit:** `feat(transport): gateway and LAN HTTP on Ktor in common code`. PR 2 = Tasks 4–5.

### Task 6: Direct MQTT on iOS

**Files:**
- Create: `core/transport/src/commonMain/.../MqttClientPort.kt`:

```kotlin
/** The low-level MQTT 3.1.1 client the iOS direct link drives. Callback-based so Swift can implement it. */
interface MqttClientPort {
    fun connect(spec: TransportSpec.Direct, listener: MqttPortListener)
    fun subscribe(filters: List<String>, qos: Int, done: (error: String?) -> Unit)
    fun unsubscribe(filters: List<String>, done: (error: String?) -> Unit)
    fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean, done: (error: String?) -> Unit)
    fun disconnect()
}

interface MqttPortListener {
    fun onConnected()
    fun onConnectionLost(kind: PortFailure, message: String)
    fun onMessage(topic: String, payload: ByteArray, retained: Boolean)
}

enum class PortFailure { UNREACHABLE, REJECTED, TLS, UNSUPPORTED, OTHER }
```

- Create: `commonMain/.../PortMqttTransport.kt`: `class PortMqttTransport(spec: TransportSpec.Direct, scope: CoroutineScope, port: MqttClientPort, reconnectInitial: Duration = 1.seconds, reconnectMax: Duration = 30.seconds, publishTimeout: Duration = 10.seconds) : ManagedTransport`, with `val failure: StateFlow<DirectFailure?>`. It matches `DirectMqttTransport`'s observable behaviour: `LinkState` sequence, `Reconnecting(attempt)` with `Backoff`, resubscribe-all after reconnect, subscription diffs, `PublishFailed` when disconnected or after `publishTimeout`, `REJECTED` and `UNSUPPORTED` → `Offline` with no retry, and `TLS` → `DirectFailure.Tls`.
- Test: `commonTest/.../PortMqttTransportTest.kt` with a `FakeMqttClientPort`. Port every `DirectMqttTransportTest` scenario (today it runs against moquette) to the fake, then add `unsupportedWsIsOffline`.
- **Gate (Step 1) picks one of the two:**
  - **A (Kotlin library):** `iosMain/.../KmqttClientPort.kt` (or the chosen library's name) and `iosTest/.../MqttClientPortIntegrationTest.kt`.
  - **B (Swift fallback, spec §9):** `iosApp/Nidoo/Mqtt/CocoaMqttClientPort.swift` (CocoaMQTT via SwiftPM in `iosApp/project.yml`). `:shared` exports `:core:transport` (`export(project(":core:transport"))` + `api`), and `MainViewController(mqttPort: MqttClientPort)` takes the port.
- Create: `iosMain/.../PlatformSignals.kt`: `fun iosNetworkChanges(): Flow<Unit>` (an `NWPathMonitor` callback flow that emits when `pathChanged` says so) and `fun iosForeground(): Flow<Boolean>` (`UIApplicationDidBecomeActive`/`DidEnterBackground` notifications). `commonMain/.../PathSnapshot.kt` holds `data class PathSnapshot(val satisfied: Boolean, val interfaces: Set<String>)` and `fun pathChanged(previous: PathSnapshot?, current: PathSnapshot): Boolean`, tested in `commonTest` (first snapshot → true, identical → false, satisfied flip → true, Wi-Fi→cellular → true).
- Modify: `iosApp/project.yml` Info properties: `NSLocalNetworkUsageDescription` = "Nidoo talks to your home hub on the local network." and `NSAppTransportSecurity: { NSAllowsLocalNetworking: true }`.
- Modify: `.github/workflows/ci.yml` iOS job: before Gradle, `brew install mosquitto`, then write a config with listener 1883 (password file made with `mosquitto_passwd -b -c`, user `nidoo`/`ci-only`) and listener 9001 `protocol websockets`, and start `mosquitto -d`. Export `NIDOO_TEST_BROKER=127.0.0.1`. Add a separate step with `continue-on-error: true` that runs the TLS cases with `NIDOO_TEST_TLS_BROKER=test.mosquitto.org:8886` (Let's Encrypt certificate, system trust like Android's `sslWithDefaultConfig`).
- Modify: `build-logic/.../nidoo.kmp.ios.gradle.kts`: for `KotlinNativeSimulatorTest`, forward `NIDOO_TEST_BROKER`/`NIDOO_TEST_TLS_BROKER` from the environment as `SIMCTL_CHILD_*`. If simctl drops them, generate them into a test source file instead.

**Interfaces:**
- Consumes: Task 4's `ManagedTransport`, `LinkState`, `Backoff`, `DirectFailure`, `TransportSpec.Direct`.
- Produces: `PortMqttTransport` and an iOS `MqttClientPort` (A: `KmqttClientPort()`; B: provided by Swift through `MainViewController`), `iosNetworkChanges()` and `iosForeground()` for the Phase 4 shell.

- [ ] **Step 1: Gate.** Check the leading Kotlin Multiplatform MQTT client (start with KMQTT, `io.github.davidepianca98:kmqtt-client`) against these criteria:
  - publishes `iosArm64` and `iosSimulatorArm64`;
  - works with Kotlin 2.4.20;
  - a release in the last 12 months and a permissive license;
  - MQTT 3.1.1, QoS 1, retained messages, username/password;
  - TLS with system trust on iOS;
  - websocket transport with a path.

  All pass → A. Otherwise → B. Record the outcome, with the failing criterion, as a dated line under spec §9.
- [ ] **Step 2: Write `PortMqttTransportTest` and the `pathChanged` tests.** Run `./gradlew :core:transport:jvmTest`. Expected: FAIL (types not defined).
- [ ] **Step 3: Implement `PortMqttTransport`, `PathSnapshot`/`pathChanged`** and the platform signal flows. Rerun Step 2. Expected: PASS.
- [ ] **Step 4 (A): Write `MqttClientPortIntegrationTest`.** Each case returns early when its env var is missing:
  - `retainedMessageArrivesOnSubscribe`;
  - `qos1RoundTrip`;
  - `wrongPasswordIsRejected` (→ `PortFailure.REJECTED`);
  - `wsProfileUsesMqttPath` (port 9001; if the library can't connect over websockets, `connect` reports `UNSUPPORTED`, and the test checks for that report instead);
  - `tlsWithSystemTrust` (the TLS broker).

  Then implement `KmqttClientPort`.
- [ ] **Step 4 (B): Implement `CocoaMqttClientPort.swift`** and pass it in from `iOSApp.swift`. Expected: `xcodebuild … -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` succeeds. The integration cases become manual checks on the phone, listed in the PR.
- [ ] **Step 5: Verify locally.** Run `./gradlew :core:transport:jvmTest :core:transport:linkDebugTestIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64 :app:assembleDebug`. Expected: PASS. Then push and check CI: the iOS job is green, the integration cases ran (not skipped), and the log shows they did.
- [ ] **Step 6: Commit:** `feat(transport): direct MQTT on iOS`. PR 3.

### Task 7: `:core:data` goes multiplatform on DataStore-Okio

**Files:**
- Modify: `core/data/build.gradle.kts`: `nidoo.kmp.library` + serialization (`android { namespace = "com.gekkotron.nidoo.data"; withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" } }`). `commonMain` gets `api(coroutines)`, `implementation(androidx-datastore-core-okio, okio)` (same `datastore` version) and `implementation(project(":core:crypto"))`.
- Move: `src/main` → `commonMain`, except `KeystoreSecretStore` (→ `androidMain`). `src/test` → `commonTest` where possible (`FakeSecretStore` included), the rest → `androidHostTest`. `src/androidTest` → `androidDeviceTest`.
- Rewrite: `AppPreferences`, `ProfileRepository` and `FileEntityCache` build their store with `DataStoreFactory.create(OkioStorage(FileSystem.SYSTEM, serializer, producePath = { path }), corruptionHandler = …, scope = …)`. Their `Serializer` objects become `OkioSerializer`s that write **the same bytes**.
- Common factories: `AppPreferences.create(path: okio.Path, scope: CoroutineScope)`, `ProfileRepository.create(path: Path, secrets: SecretStore, scope: CoroutineScope)`, `FileEntityCache.create(path: Path, scope: CoroutineScope)`. In `androidMain`, the existing `create(context, …)` and `create(file: File, …)` overloads stay, with the same paths (`filesDir`, `noBackupFilesDir`, same `FILE` names), so no Android call site changes.
- Create: `iosMain/.../IosDataPaths.kt`: `fun nidooDataPath(file: String): Path` (Application Support/`Nidoo`/, directories created on first use) and `fun nidooCachePath(file: String): Path` (same directory, with `NSURLIsExcludedFromBackupKey` set).
- Rewrite in `OnboardingPayload`: `isGatewayUrl` uses a small common parser for scheme, authority and port instead of `java.net.URI`, and `isLooseHost` accepts Unicode letters directly instead of going through `IDN.toASCII`. The existing `OnboardingPayloadTest` cases are the spec for both.
- Test: `androidHostTest/.../LegacyFilesTest.kt` + `androidHostTest/resources/legacy/{preferences,profiles,entity-cache}` files.

**Interfaces:**
- Produces: common `AppPreferences`, `ProfileRepository`, `FileEntityCache`, `EntityCache`, `SecretStore`, `SecretUnavailable`, `OnboardingPayload`, `ConnectionProfile`, `HistoryUrls`, `isLikelyLocalHost`, `nidooDataPath`/`nidooCachePath` (iOS).

- [ ] **Step 1: Capture legacy files before any change.** Add a throwaway host test that writes, with today's code, a non-default preferences state (theme, language and every other field), a profile with gateway secrets (using `FakeSecretStore`), and a 3-entry entity cache. Copy the three files into `src/test/resources/legacy/` (they move to `androidHostTest/resources/legacy/` in Step 3), then delete the throwaway test.
- [ ] **Step 2: Write `LegacyFilesTest`** (`readsLegacyPreferences`, `readsLegacyProfilesAndSecrets`, `readsLegacyEntityCache`, `rewrittenFileIsByteIdentical` (load then save with no change → same bytes)). Run it on the old layout with `./gradlew :core:data:test`. Expected: PASS. It pins down today's behaviour.
- [ ] **Step 3: Convert the module** as listed in Files.
- [ ] **Step 4: Verify.** Run `./gradlew :core:data:testAndroidHostTest :core:data:compileKotlinIosArm64 :core:data:linkDebugTestIosSimulatorArm64 :core:alerts:test :app:testDebugUnitTest :app:assembleDebug`. Expected: PASS, including `LegacyFilesTest` unchanged, with the test count at least the baseline.
- [ ] **Step 5: Commit:** `refactor(data): multiplatform storage on DataStore-Okio, same files on Android`.

### Task 8: Keychain-backed `SecretStore` on iOS

**Files:**
- Create: `core/data/src/iosMain/.../KeychainSecretStore.kt`: `class KeychainSecretStore(service: String = "com.gekkotron.nidoo.secrets", account: String = "nidoo.secrets.v1", keychain: Keychain = SystemKeychain) : SecretStore`. On first use it reads the 32-byte key, or creates it with `AesGcm256.newKey()` and adds it. `encrypt` = `AesGcm256.sealWithRandomNonce`, `decrypt` = `AesGcm256.openNonceFirst`. Every failure becomes `SecretUnavailable`. **An existing key is never overwritten:** on `errSecDuplicateItem` it re-reads the stored key, and on any read status other than "not found" it throws.
- Create: `iosMain/.../Keychain.kt`: `internal interface Keychain { fun read(service: String, account: String): KeychainRead; fun add(service: String, account: String, value: ByteArray): Int /* OSStatus */ }`, with `sealed interface KeychainRead { data class Found(val value: ByteArray); data object NotFound; data class Failed(val status: Int) }`. `internal object SystemKeychain : Keychain` uses `SecItemCopyMatching`/`SecItemAdd` with `kSecClassGenericPassword` and `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
- Test: `iosTest/.../KeychainSecretStoreTest.kt` with a `FakeKeychain`.

**Interfaces:**
- Consumes: Task 1's `AesGcm256.sealWithRandomNonce`/`openNonceFirst`/`newKey`, Task 7's `SecretStore`.
- Produces: `KeychainSecretStore` for the Phase 4 shell (`ProfileRepository.create(nidooDataPath("profiles.json"), KeychainSecretStore(), scope)`; the file name is whatever Android's `FILE` constant is).

- [ ] **Step 1: Write the tests:**
  - `roundTrip`;
  - `keyIsCreatedOnceAndReused` (two store instances share the fake; the second one decrypts the first one's output, and `add` is called once);
  - `tamperedCipherIsUnavailable`;
  - `lockedKeychainIsUnavailable` (read returns `Failed(-25308)` → `SecretUnavailable`, and `add` is never called);
  - `existingKeyIsNeverReplaced` (`add` returns `-25299` duplicate → the store re-reads and uses the stored key).

  Run `./gradlew :core:data:linkDebugTestIosSimulatorArm64`. Expected: compile failure (types not defined).
- [ ] **Step 2: Implement** `Keychain`, `SystemKeychain` and `KeychainSecretStore`. Rerun the link. Expected: success.
- [ ] **Step 3: Push and check CI** (the iOS job runs `:core:data:iosSimulatorArm64Test`). Expected: green.
- [ ] **Step 4: Commit:** `feat(data): Keychain-backed secret store on iOS`. PR 4 = Tasks 7–8.

### Task 9: `:core:state` `jvmMain` → `commonMain`

**Files:**
- Move: `core/state/src/jvmMain/.../{StateStore,CommandTracker}.kt` → `commonMain`, with the common replacements from Global Constraints. `jvmTest` → `commonTest`.

- [ ] **Step 1: Record the test count**, then move the files. Run `./gradlew :core:state:jvmTest :core:state:linkDebugTestIosSimulatorArm64 :app:testDebugUnitTest`. Expected: PASS, count unchanged.
- [ ] **Step 2: Commit:** `refactor(state): StateStore and CommandTracker in common code`. PR 5.

### Task 10: `:core:source` `jvmMain` → `commonMain`

**Files:**
- Modify: `core/source/build.gradle.kts`: move `api(project(":core:transport"))` to `commonMain`. `jvmTest` uses `project(":core:transport-testing")`.
- Move: `EntitySource`, `MqttDiscoverySource`, `discovery/{Abbreviations,DiscoveryParser}` → `commonMain`. Tests that only use `FakeMqttTransport` → `commonTest`, the `EmbeddedBroker` ones stay in `jvmTest`.

- [ ] **Step 1: Record the test count**, then move the files. Run `./gradlew :core:source:jvmTest :core:source:linkDebugTestIosSimulatorArm64 :feature:home:testDebugUnitTest :app:testDebugUnitTest`. Expected: PASS, count unchanged.
- [ ] **Step 2: Commit:** `refactor(source): MQTT discovery in common code`. PR 6.

### Task 11: `:core:history` `jvmMain` → `commonMain` (history API on Ktor)

**Files:**
- Modify: `core/history/build.gradle.kts`: move `api(project(":core:transport"))` to `commonMain`, and add `kotlinx-datetime` if `LastChange` needs zones. `commonTest` gets `ktor-client-mock`.
- Move: `HistoryClient`, `DemoHistoryClient`, `LastChange` → `commonMain`. `HistoryApi` is rewritten on `HttpClient` (it goes through `LanOrGatewayGet` wherever it does today). If `app/di/HistoryModule.kt` builds it from an `OkHttpClient`, add a same-named `jvmMain` factory so the call site doesn't change (the pattern from Task 5).
- Tests: `jvmTest` → `commonTest`. A test that needs MockWebServer stays in `jvmTest`.

- [ ] **Step 1: Record the test count**, then move and rewrite the files. Run `./gradlew :core:history:jvmTest :core:history:linkDebugTestIosSimulatorArm64 :feature:history:testDebugUnitTest :app:assembleDebug`. Expected: PASS, count unchanged.
- [ ] **Step 2: Commit:** `refactor(history): history client in common code on Ktor`. PR 7.

### Task 12: `:core:demo` in common code, and the demo house on the iOS screen

**Files:**
- Modify: `core/demo/build.gradle.kts`: `nidoo.kmp.jvm.library` + serialization. `commonMain` gets `api(project(":core:transport"))`, and `commonTest` gets `:core:source`, `:core:dashboards`, `:core:climate`.
- Move: `src/main` → `commonMain`, `src/test` → `commonTest`.
- Modify: `shared/build.gradle.kts`: `commonMain` gets `implementation(project(":core:demo"), project(":core:source"))`.
- Create: `shared/src/commonMain/.../DemoHouseSummary.kt`: `suspend fun demoHouseSummary(scope: CoroutineScope): String`. It starts `DemoBroker` and runs `MqttDiscoverySource` over it until discovery settles, then returns `"Demo house: $n devices"`.
- Modify: `NidooRoot.kt`: a third line showing `demoHouseSummary` (shows `"…"` while loading).
- Test: `shared/src/commonTest/.../DemoHouseSummaryTest.kt`: `countsDemoHouseDevices` asserts the exact device count that `DemoHouseTest` already pins.
- Modify: `.github/workflows/ci.yml`: `:core:demo:test` → `:core:demo:jvmTest`. The iOS job gets `:core:demo:iosSimulatorArm64Test`, plus `:core:crypto`, `:core:transport`, `:core:transport-testing` and `:core:data` if an earlier task missed one.

- [ ] **Step 1: Write `DemoHouseSummaryTest`.** Run `./gradlew :shared:testAndroidHostTest`. Expected: FAIL (not defined).
- [ ] **Step 2: Move `:core:demo`, implement `demoHouseSummary` and update `NidooRoot`.** Run `./gradlew :core:demo:jvmTest :shared:testAndroidHostTest :shared:linkDebugFrameworkIosArm64 :app:assembleDebug`. Expected: PASS.
- [ ] **Step 3: Build the iOS app without booting anything:** `xcodegen generate --spec iosApp/project.yml && xcodebuild -project iosApp/Nidoo.xcodeproj -scheme Nidoo -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`. Expected: BUILD SUCCEEDED. Ask the user whether they want to see it on their iPhone, or in the Simulator (which needs their yes).
- [ ] **Step 4: Commit:** `feat(shared): demo house over the shared transport on iOS`. PR 8.
- [ ] **Step 5: Update memory** (`ios-compose-multiplatform.md`): Phase 2 is done, plus the Task 6 gate outcome and the SDK coordinates.

# Kotlin Multiplatform architecture

## Status

Accepted for the Compose Multiplatform migration. New shared features should follow this model;
existing features move to it one vertical slice at a time.

## Goals

- Keep application data and behavior consistent on Android, iOS, and desktop.
- Give every mutable value one clear owner.
- Let multiple scenes or windows share repositories without sharing navigation or screen state.
- Expose immutable, observable state to Compose and native hosts.
- Keep native facilities behind small ports and execute them only after shared code has decided what
  should happen.

## Ownership

### Application component

One `HarmonicAppComposition` exists for a running application process. It owns long-lived services:

- network clients and repositories;
- settings, accounts, history, saved items, and persistent caches;
- cross-feature update buses and application policies;
- factories for scene and feature objects.

It must not own navigation history, a current screen, scroll position, dialogs, or a window's
coroutine scope.

### Scene component

Each Android activity task, iOS scene, or desktop window owns one `HarmonicSceneComposition`. It
owns:

- the main navigation store;
- screen-session retention for that scene;
- transient user messages for that scene;
- scene link and launch routing;
- the lifecycle of feature stores opened by the scene.

Closing a scene releases its feature stores and transient state without closing application
repositories. Two scenes may therefore display independent destinations while observing the same
saved items and settings.

### Feature store

Each navigation entry owns a feature store with one public state stream and one effect stream:

```kotlin
interface FeatureStore<Intent, State, Effect> {
    val state: StateFlow<State>
    val effects: Flow<Effect>
    fun accept(intent: Intent)
    fun close()
}
```

`State` is an immutable snapshot. `Intent` describes user or lifecycle input. `Effect` is reserved
for work that cannot be represented as state, such as opening a native browser or presenting a
platform-owned document picker.

A feature may use repositories, reducers, request sessions, and private mutable working models
internally. Those implementation objects do not cross the store boundary. In particular, a
presenter, runtime, screen session, controller, and platform coordinator must not each republish the
same feature state.

## Data flow

The canonical flow is:

```text
platform transport/storage -> repository -> feature store -> immutable feature state -> UI
                                                |
                                                +-> platform effect -> scene host

UI intent -> feature store -> repository or reducer -> new immutable feature state
```

Rules:

1. Network DTOs are mapped at the repository boundary.
2. Domain content is immutable when exposed outside a repository or feature store.
3. Loading, selection, preview, tint, and other presentation facts live in feature state rather
   than on the domain entity.
4. UI code collects state. It does not query several owners and assemble a current state snapshot
   on demand.
5. Hosts translate lifecycle input and platform effects; they do not decide feature policy.
6. Every coroutine scope has an explicit owner and an idempotent close/dispose path.

## Platform boundaries

`commonMain` owns domain models, repositories, feature stores, navigation policy, settings
semantics, and shared Compose UI. Hosts own facilities whose implementation is genuinely native:

- Ktor engines and native network/cache integration;
- encrypted credentials and Keychain/Keystore access;
- browser engines, intents, sharing, clipboard, and document access;
- notifications, background scheduling, and local inference engines;
- platform directories, clock/display integration, and scene lifecycle.

Prefer constructor-supplied interfaces to `expect`/`actual` when a runtime capability can be
injected. Optional product features may be nullable at the application boundary, but core feature
dependencies should be required before creating the feature.

## Migration sequence

1. Split scene state from `HarmonicAppComposition` while preserving the Android UI.
2. Make Stories the reference feature: one store, immutable output, and a real desktop host.
3. Apply the same contract to Comments while keeping native browser drivers in platform code.
4. Move remaining features as their screens are migrated.
5. Remove compatibility presenters, sessions, controllers, and mutable UI model exposure after
   their final caller moves.

The migration is complete when Android and at least one other host construct the same feature store,
render its state, send it intents, handle its effects, and dispose it through the same lifecycle
contract.

## Deliberate non-goals

- Introducing a dependency-injection framework.
- Creating a Gradle module for every feature before boundaries stabilize.
- Sharing native browser, notification, or background-work implementations.
- Replacing working persistence formats merely to make them newer.
- Rewriting all features at once.

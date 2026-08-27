# StreamApp — Architectural Overview & Design Patterns

## 1. Architectural Layers

```
Presentation (Compose UI, ViewModels, UiState, UiEffect)
       ↓
Domain (UseCases, Models, Repository Interfaces, StreamingClient Contract)
       ↓
Data & Infrastructure (Room DB, DataStore, Repositories, WebRTC/Fake Engine, MediaCodec, AudioManager)
```

### Layer Isolation Rules:
1. **Presentation Layer:**
   - Composable functions strictly render `UiState` and dispatch user events.
   - Zero direct references to Room DAOs, Retrofit, or transport libraries.
2. **Domain Layer:**
   - Pure Kotlin standard library.
   - Represents all business models (`StreamServer`, `StreamAppInfo`, `StreamConfiguration`, `StreamStatistics`, `NormalizedInputEvent`).
   - Declares the `StreamingClient` interface.
3. **Data & Infrastructure Layer:**
   - Single Source of Truth using Room Database for cached games, servers, and controller layouts.
   - Encapsulates hardware codecs and WebRTC transport.

---

## 2. Streaming Engine Decoupling

The `StreamingClient` interface decouples the UI from WebRTC, Moonlight, or custom streaming engines:

```kotlin
interface StreamingClient {
    suspend fun connect(config: StreamConfiguration): AppResult<Unit>
    suspend fun disconnect()
    fun connectionState(): StateFlow<StreamingConnectionState>
    fun statistics(): StateFlow<StreamStatistics>
    suspend fun sendInput(event: NormalizedInputEvent)
    fun attachVideoSurface(surface: Surface)
    fun detachVideoSurface()
    fun setStreamVolume(volume: Float)
}
```

Implementations:
- `FakeStreamingClient`: High-fidelity synthetic rendering engine for development, previews, and CI testing.
- `WebRtcStreamingClient`: Production WebRTC transport.
- Extensible for `MoonlightStreamingClient` or `CustomUdpStreamingClient`.

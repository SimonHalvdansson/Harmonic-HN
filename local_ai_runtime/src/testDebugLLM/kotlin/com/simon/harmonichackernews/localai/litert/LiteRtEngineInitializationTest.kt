package com.simon.harmonichackernews.localai.litert

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtEngineInitializationTest {
  @Test
  fun failedGpuInitializationFallsBackToCpuWithoutClosingUninitializedEngine() {
    val gpu = FakeEngine(failsInitialization = true)
    val cpu = FakeEngine()
    val attempts = mutableListOf<Boolean>()

    val result = initializeEngineWithCpuFallback(
      preferGpu = true,
      create = { useGpu -> attempts += useGpu; if (useGpu) gpu else cpu },
      initialize = FakeEngine::initialize,
      isInitialized = { it.initialized },
    )

    assertSame(cpu, result)
    assertEquals(listOf(true, false), attempts)
    assertTrue(cpu.initialized)
    assertFalse(gpu.closed)
  }

  @Test
  fun initializedGpuIsReturnedWithoutCreatingCpu() {
    val gpu = FakeEngine()
    val result = initializeEngineWithCpuFallback(
      preferGpu = true,
      create = { useGpu -> check(useGpu); gpu },
      initialize = FakeEngine::initialize,
      isInitialized = { it.initialized },
    )
    assertSame(gpu, result)
    assertFalse(gpu.closed)
  }

  @Test
  fun cpuPreferenceSkipsGpuAndPropagatesInitializationFailure() {
    val cpu = FakeEngine(failsInitialization = true)
    val failure = assertThrows(IllegalStateException::class.java) {
      initializeEngineWithCpuFallback(
        preferGpu = false,
        create = { useGpu -> check(!useGpu); cpu },
        initialize = FakeEngine::initialize,
        isInitialized = { it.initialized },
      )
    }
    assertEquals("Backend unavailable", failure.message)
    assertFalse(cpu.closed)
  }

  @Test
  fun upstreamEngineRejectsClosingBeforeInitialization() {
    // Exercises the actual bundled LiteRT contract without loading a native library or model.
    val engine = Engine(EngineConfig(modelPath = "unused.litertlm", backend = Backend.GPU()))
    assertFalse(engine.isInitialized())
    assertThrows(IllegalStateException::class.java) { engine.close() }
  }

  private class FakeEngine(private val failsInitialization: Boolean = false) : AutoCloseable {
    var initialized = false
    var closed = false

    fun initialize() {
      check(!failsInitialization) { "Backend unavailable" }
      initialized = true
    }

    override fun close() {
      check(initialized) { "Engine is not initialized" }
      closed = true
    }
  }
}

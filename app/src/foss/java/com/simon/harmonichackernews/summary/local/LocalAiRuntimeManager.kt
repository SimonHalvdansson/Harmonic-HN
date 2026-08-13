package com.simon.harmonichackernews.summary.local

import android.content.Context
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import com.simon.harmonichackernews.summary.LocalRuntimeInstallStatus

internal fun createAndroidLocalRuntimeDelivery(context: Context): AndroidLocalRuntimeDelivery =
    object : AndroidLocalRuntimeDelivery {
        private val unavailableMessage = "Local AI is not included in the FOSS distribution."

        override val included: Boolean = false

        override fun status(runtime: LocalModelRuntime): LocalRuntimeInstallStatus =
            LocalRuntimeInstallStatus(
                state = LocalRuntimeInstallState.NOT_INSTALLED,
                runtime = runtime,
                error = unavailableMessage,
            )

        override fun isInstalled(runtime: LocalModelRuntime): Boolean = false

        override fun request(model: com.simon.harmonichackernews.summary.LocalModelDefinition): String =
            unavailableMessage

        override fun cancel(runtime: LocalModelRuntime) = Unit

        override fun setObserver(observer: () -> Unit) = observer()

        override fun setModelDownloadStarter(starter: (String) -> String?) = Unit

        override fun engineClassName(runtime: LocalModelRuntime): String? = null

        override fun runtimeLabel(runtime: LocalModelRuntime): String = when (runtime) {
            LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
            else -> "local AI runtime"
        }
    }

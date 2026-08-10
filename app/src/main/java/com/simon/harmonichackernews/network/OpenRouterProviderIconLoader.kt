package com.simon.harmonichackernews.network

/** Android callback bridge for the shared provider-icon resolver. */
object OpenRouterProviderIconLoader {
    fun resolve(providerSlug: String?, listener: CallbackListener) {
        NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.openRouterProviderIconRepository.resolve(providerSlug) },
            onSuccess = { result ->
                val iconData: Any? = when (val icon = result.icon) {
                    is OpenRouterProviderIcon.RemoteUrl -> icon.url
                    is OpenRouterProviderIcon.SvgBytes -> icon.bytes
                    null -> null
                }
                listener.onResolved(result.providerSlug, iconData)
            },
            onFailure = {
                listener.onResolved(OpenRouterProviderIconParser.normalizeSlug(providerSlug), null)
            },
        )
    }

    fun interface CallbackListener {
        fun onResolved(providerSlug: String, iconData: Any?)
    }
}

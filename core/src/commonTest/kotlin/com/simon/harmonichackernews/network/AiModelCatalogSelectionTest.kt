package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals

class AiModelCatalogSelectionTest {
    @Test
    fun excludesBatchAndProModels() {
        val selected = AiModelCatalogSelection.cheapestModel(
            listOf(
                model("provider/cheap-batch", "Cheap batch", inputPrice = 0.0),
                model("provider/cheap-pro", "Cheap pro", inputPrice = 0.0),
                model("provider/eligible", "Eligible", inputPrice = 0.0),
            ),
            createdAfter = 0L,
        )

        assertEquals("provider/eligible", selected?.openRouterId)
    }

    @Test
    fun prefersNewerModelBeforeShorterTitle() {
        val selected = AiModelCatalogSelection.cheapestModel(
            listOf(
                model("provider/short", "Short", created = 1L),
                model("provider/newer", "Much longer title", created = 2L),
            ),
            createdAfter = 0L,
        )

        assertEquals("provider/newer", selected?.openRouterId)
    }

    @Test
    fun usesShorterTitleWhenPriceAndCreationTimeTie() {
        val selected = AiModelCatalogSelection.cheapestModel(
            listOf(
                model("provider/long", "A longer title", created = 1L),
                model("provider/short", "Short", created = 1L),
            ),
            createdAfter = 0L,
        )

        assertEquals("provider/short", selected?.openRouterId)
    }

    private fun model(
        openRouterId: String,
        name: String,
        created: Long = 1L,
        inputPrice: Double = 0.001,
    ) = AiModel(
        openRouterId = openRouterId,
        requestId = openRouterId,
        name = name,
        created = created,
        inputPrice = inputPrice,
        outputPrice = 0.0,
        contextLength = 1L,
    )
}

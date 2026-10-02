package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun excludesQualifiedVariantsEvenWhenTheyAreCheaperAndNewer() {
        for (variant in listOf(
            "luna-pro:batch", "luna:batch", "luna:free", "luna:extended",
            "luna:online", "luna:future-qualifier", "luna-PRO", "luna-BATCH",
            "luna-pro-20261001", "luna-batch-20261001", "luna_pro", "luna_batch",
        )) {
            val selected = AiModelCatalogSelection.cheapestModel(
                listOf(
                    model("provider/$variant", variant, created = 2L, inputPrice = 0.0),
                    model("provider/luna", "Luna"),
                ),
                createdAfter = 0L,
            )

            assertEquals("provider/luna", selected?.openRouterId, variant)
        }
    }

    @Test
    fun doesNotFallBackToExcludedVariantsWhenNoPlainModelIsEligible() {
        val models = listOf(
            model("provider/luna-pro:batch", "Luna Pro Batch", created = 2L),
            model("provider/luna-pro", "Luna Pro", created = 2L),
            model("provider/luna", "Luna", created = 1L),
        )

        assertNull(AiModelCatalogSelection.cheapestModel(models, createdAfter = 2L))
        assertEquals(
            "provider/luna",
            AiModelCatalogSelection.cheapestModel(models, createdAfter = Long.MIN_VALUE)?.openRouterId,
        )
        assertNull(AiModelCatalogSelection.cheapestModel(models.take(2), Long.MIN_VALUE))
    }

    @Test
    fun keepsOrdinaryModelNamesAndProviderNamespacesEligible() {
        for (id in listOf("pro/luna", "batch/luna", "provider/luna-prototype")) {
            assertEquals(
                id,
                AiModelCatalogSelection.cheapestModel(listOf(model(id, id)), 0L)?.openRouterId,
            )
        }
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

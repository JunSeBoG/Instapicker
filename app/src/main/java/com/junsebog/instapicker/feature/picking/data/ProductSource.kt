package com.junsebog.instapicker.feature.picking.data

import android.content.Context
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Provisioning seam for the session's baseline products. The repository depends only
 * on this interface, so a real backend can replace the asset-based source without
 * touching any domain code.
 */
interface ProductSource {
    suspend fun load(): List<PickItem>
}

/** JSON shape of one seeded product; kept separate from the domain [PickItem]. */
@Serializable
private data class SeedProduct(
    val id: String,
    val name: String,
    val ean13: String,
    val qty: Int,
)

/**
 * Reads the bundled `seed_products.json` asset. Deterministic and fully offline, so
 * the app works on the warehouse floor without a network.
 */
class AssetProductSource(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ProductSource {

    override suspend fun load(): List<PickItem> = withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        json.decodeFromString<List<SeedProduct>>(text).map { it.toPickItem() }
    }

    private fun SeedProduct.toPickItem() = PickItem(
        id = id,
        name = name,
        ean13 = ean13,
        requestedQty = qty,
        remainingToScan = qty,
        state = PickState.PENDING,
    )

    private companion object {
        const val ASSET_NAME = "seed_products.json"
    }
}

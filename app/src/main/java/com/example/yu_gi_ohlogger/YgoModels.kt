package com.example.yu_gi_ohlogger

import com.google.gson.annotations.SerializedName

data class YgoCardSetInfo(
    @SerializedName("set_name") val setName: String?,
    @SerializedName("set_code") val setCode: String?
)

data class YgoCardSetDetail(
    @SerializedName("set_code") val setCode: String?,
    @SerializedName("set_name") val setName: String?,
    @SerializedName("set_rarity") val setRarity: String?
)

data class YgoCardImage(
    @SerializedName("image_url_small") val imageUrlSmall: String?
)

data class YgoCard(
    val name: String,
    @SerializedName("card_sets") val cardSets: List<YgoCardSetDetail>?,
    @SerializedName("card_images") val cardImages: List<YgoCardImage>?
)

data class YgoApiResponse(
    val data: List<YgoCard>?
)

data class CardLogEntry(
    val cardName: String,
    val quantity: Int,
    val rarity: String,
    val edition: String,
    val setName: String,
    val setCode: String
)
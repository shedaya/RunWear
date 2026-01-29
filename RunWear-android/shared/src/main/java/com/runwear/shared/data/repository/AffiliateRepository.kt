package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.AffiliatePartner
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing affiliate links and partner configuration.
 */
@Singleton
class AffiliateRepository @Inject constructor() {

    /**
     * Get the user's affiliate partner (defaults to Amazon).
     * In the future, this could be personalized based on user location or preferences.
     */
    suspend fun getUserPartner(): AffiliatePartner {
        return AffiliatePartner.AMAZON
    }

    /**
     * Build an affiliate link for a clothing item.
     */
    fun buildAffiliateLink(
        partner: AffiliatePartner,
        item: ClothingItem,
        gender: GenderPreference
    ): String {
        val genderSuffix = when (gender) {
            GenderPreference.MALE -> "mens"
            GenderPreference.FEMALE -> "womens"
            GenderPreference.UNISEX -> ""
        }

        val searchTerm = if (genderSuffix.isNotEmpty()) {
            "$genderSuffix ${item.amazonSearchTerm}"
        } else {
            item.amazonSearchTerm
        }

        return when (partner) {
            AffiliatePartner.AMAZON -> buildAmazonLink(searchTerm)
            AffiliatePartner.REI -> buildReiLink(searchTerm)
            AffiliatePartner.DICKS -> buildDicksLink(searchTerm)
        }
    }

    private fun buildAmazonLink(searchTerm: String): String {
        val encoded = searchTerm.replace(" ", "+")
        return "https://www.amazon.com/s?k=$encoded&tag=runwear-20"
    }

    private fun buildReiLink(searchTerm: String): String {
        val encoded = searchTerm.replace(" ", "+")
        return "https://www.rei.com/search?q=$encoded"
    }

    private fun buildDicksLink(searchTerm: String): String {
        val encoded = searchTerm.replace(" ", "+")
        return "https://www.dickssportinggoods.com/search/$encoded"
    }
}

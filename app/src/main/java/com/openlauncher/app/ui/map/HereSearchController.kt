package com.openlauncher.app.ui.map

import com.here.sdk.core.GeoCoordinates
import com.here.sdk.core.LanguageCode
import com.here.sdk.core.errors.InstantiationErrorException
import com.here.sdk.search.CategoryQuery
import com.here.sdk.search.PlaceCategory
import com.here.sdk.search.SearchCallback
import com.here.sdk.search.SearchEngine
import com.here.sdk.search.SearchError
import com.here.sdk.search.SearchOptions
import com.here.sdk.search.TextQuery

data class HereSearchResult(
    val title: String,
    val address: String,
    val coordinates: GeoCoordinates,
    val accessPoints: List<GeoCoordinates>,
    val distanceMeters: Double
)

data class HereSelectedLocation(
    val coordinates: GeoCoordinates,
    val title: String?,
    val address: String?
)

enum class HereNearbyCategory(
    val categoryIds: List<String>
) {
    FUEL(
        listOf(
            "700-7600-0000",
            "700-7600-0116"
        )
    ),

    PARKING(
        listOf(
            "800-8500-0000",
            "800-8500-0177",
            "800-8500-0178",
            "800-8500-0179"
        )
    ),

    FOOD(
        listOf(
            "100-1000",
            "100-1100"
        )
    ),

    SHOPPING(
        listOf(
            "600"
        )
    )
}

class HereSearchController {

    private val searchEngine: SearchEngine =
        try {
            SearchEngine()
        } catch (e: InstantiationErrorException) {
            throw RuntimeException(
                "Failed to initialize SearchEngine: ${e.error.name}",
                e
            )
        }

    fun search(
        queryText: String,
        center: GeoCoordinates,
        onSuccess: (List<HereSearchResult>) -> Unit,
        onError: (SearchError) -> Unit
    ) {
        val query = TextQuery(
            queryText,
            TextQuery.Area(center)
        )

        val options = SearchOptions().apply {
            languageCode = LanguageCode.EN_GB
            maxItems = 8
        }

        searchEngine.searchByText(
            query,
            options,
            SearchCallback { searchError, places ->
                if (searchError != null) {
                    onError(searchError)
                    return@SearchCallback
                }

                val results =
                    places.orEmpty()
                        .mapNotNull { place ->
                            val coordinates =
                                place.geoCoordinates
                                    ?: return@mapNotNull null

                            HereSearchResult(
                                title = place.title,
                                address = place.address.addressText,
                                coordinates = coordinates,
                                accessPoints = place.accessPoints,
                                distanceMeters = center.distanceTo(coordinates)
                            )
                        }

                onSuccess(results)
            }
        )
    }

    fun reverseGeocode(
        coordinates: GeoCoordinates,
        onSuccess: (HereSelectedLocation) -> Unit,
        onError: (SearchError) -> Unit
    ) {
        val options =
            SearchOptions().apply {
                languageCode =
                    LanguageCode.EN_GB

                maxItems = 1
            }

        searchEngine.searchByCoordinates(
            coordinates,
            options,
            SearchCallback { searchError, places ->
                if (searchError != null) {
                    onError(searchError)
                    return@SearchCallback
                }

                val place =
                    places.orEmpty()
                        .firstOrNull()

                onSuccess(
                    HereSelectedLocation(
                        coordinates = coordinates,
                        title =
                            place?.title,
                        address =
                            place?.address?.addressText
                    )
                )
            }
        )
    }

    fun searchNearby(
        category: HereNearbyCategory,
        center: GeoCoordinates,
        onSuccess: (List<HereSearchResult>) -> Unit,
        onError: (SearchError) -> Unit
    ) {
        val categories =
            category.categoryIds.map { categoryId ->
                PlaceCategory(categoryId)
            }

        val query =
            CategoryQuery(
                categories,
                CategoryQuery.Area(center)
            )

        val options =
            SearchOptions().apply {
                languageCode =
                    LanguageCode.EN_GB

                maxItems = 12
            }

        searchEngine.searchByCategory(
            query,
            options,
            SearchCallback { searchError, places ->
                if (searchError != null) {
                    onError(searchError)
                    return@SearchCallback
                }

                val results =
                    places.orEmpty()
                        .mapNotNull { place ->
                            val coordinates =
                                place.geoCoordinates
                                    ?: return@mapNotNull null

                            HereSearchResult(
                                title =
                                    place.title,
                                address =
                                    place.address.addressText,
                                coordinates =
                                    coordinates,
                                accessPoints =
                                    place.accessPoints,
                                distanceMeters =
                                    center.distanceTo(
                                        coordinates
                                    )
                            )
                        }
                        .sortedBy {
                            it.distanceMeters
                        }.let {
                            deduplicateNearbyResults(it)
                        }

                onSuccess(results)
            }
        )
    }

    private fun deduplicateNearbyResults(
        results: List<HereSearchResult>
    ): List<HereSearchResult> {

        val unique =
            mutableListOf<HereSearchResult>()

        results.forEach { candidate ->

            val duplicate =
                unique.any { existing ->

                    val distance =
                        existing.coordinates.distanceTo(
                            candidate.coordinates
                        )

                    val existingAddress =
                        normalizePlaceAddress(
                            existing.address
                        )

                    val candidateAddress =
                        normalizePlaceAddress(
                            candidate.address
                        )

                    distance <= 10.0 &&
                            existingAddress ==
                            candidateAddress
                }

            if (!duplicate) {
                unique += candidate
            }
        }

        return unique
    }

    private fun normalizePlaceAddress(
        address: String
    ): String {

        return address
            .substringAfter(
                ",",
                address
            )
            .lowercase()
            .replace(
                Regex("[^a-z0-9ąćęłńóśźż]"),
                ""
            )
    }
}
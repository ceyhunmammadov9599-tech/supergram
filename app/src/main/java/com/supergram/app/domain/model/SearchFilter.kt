package com.supergram.app.domain.model

/**
 * Content filter for message search. Domain-owned enum; the TDLib
 * SearchMessagesFilter mapping stays strictly inside the data layer.
 */
enum class SearchFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    DOCUMENTS("Documents"),
    LINKS("Links"),
}

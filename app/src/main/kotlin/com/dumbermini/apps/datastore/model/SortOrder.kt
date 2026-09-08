package com.dumbermini.apps.datastore.model

// todo: Add Support for sorting by size
enum class SortOrder {
    POPULARITY,
    UPDATED,
    ADDED,
    NAME,
    SIZE,
}

fun supportedSortOrders(): List<SortOrder> = listOf(
    SortOrder.POPULARITY,
    SortOrder.UPDATED,
    SortOrder.ADDED,
    SortOrder.NAME,
)

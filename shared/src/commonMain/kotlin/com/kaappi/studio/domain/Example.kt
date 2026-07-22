package com.kaappi.studio.domain

data class Example(
    val id: String,
    val title: String,
    val description: String,
    val code: String,
    val category: ExampleCategory,
)

enum class ExampleCategory(val label: String) {
    GETTING_STARTED("Getting Started"),
    FUNCTIONS("Functions"),
    DATA_STRUCTURES("Data Structures"),
    CONTROL_FLOW("Control Flow"),
    ADVANCED("Advanced"),
}

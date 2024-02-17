package com.asinosoft.gallery.data

import java.time.LocalDate

data class ImageGroup(
    val date: LocalDate,
    val images: List<Image>,
)

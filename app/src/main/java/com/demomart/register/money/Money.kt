package com.demomart.register.money

import kotlin.math.abs

fun formatEur(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val euros = abs(cents / 100)
    val fraction = abs(cents % 100).toString().padStart(2, '0')
    return "${sign}€$euros.$fraction"
}

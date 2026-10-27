package com.marketbrief.adapters.marketdata

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal fun percentChange(previous: BigDecimal, current: BigDecimal): BigDecimal? {
    if (previous.signum() == 0) return null
    return current.subtract(previous)
        .divide(previous, MathContext.DECIMAL64)
        .multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_UP)
}

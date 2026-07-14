package cn.limpu.hita.utils

import java.math.BigDecimal

fun formatCredits(credits: Float): String {
    return BigDecimal(credits.toString()).stripTrailingZeros().toPlainString()
}

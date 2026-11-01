package com.demomart.register.money

data class AmountInput(val cents: Long = 0) {
    fun digit(value: Int): AmountInput {
        require(value in 0..9)
        return append(10, value)
    }

    fun doubleZero(): AmountInput = append(100, 0)

    fun backspace(): AmountInput = AmountInput(cents / 10)

    fun clear(): AmountInput = AmountInput()

    private fun append(multiplier: Int, digit: Int): AmountInput =
        if (cents <= (MAX_CENTS - digit) / multiplier) AmountInput(cents * multiplier + digit) else this

    companion object {
        const val MAX_CENTS = 1_000_000L
    }
}

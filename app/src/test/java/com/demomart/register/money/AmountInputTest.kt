package com.demomart.register.money

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {
    @Test fun typing2300GivesTwentyThreeEuros() =
        assertEquals(2300L, AmountInput().digit(2).digit(3).digit(0).digit(0).cents)

    @Test fun doubleZero() =
        assertEquals(2300L, AmountInput().digit(2).digit(3).doubleZero().cents)

    @Test fun backspaceDropsLastDigit() =
        assertEquals(230L, AmountInput(2300).backspace().cents)

    @Test fun ignoresOverMax() {
        assertEquals(1_000_000L, AmountInput(1_000_000).digit(1).cents)
        assertEquals(20_000L, AmountInput(20_000).doubleZero().cents)
    }

    @Test fun leadingZerosIgnored() =
        assertEquals(5L, AmountInput().digit(0).digit(0).digit(5).cents)

    @Test fun clearResetsAmount() = assertEquals(0L, AmountInput(2300).clear().cents)
}

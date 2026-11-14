package com.gogleox.scrip.account;

import com.gogleox.scrip.Scrip;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    @Test void identityUsesUuidValue() {
        var value = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(new AccountId(value), new AccountId(UUID.fromString(value.toString())));
        assertEquals(new AccountId(value).hashCode(), new AccountId(value).hashCode());
        assertNotEquals(new AccountId(value), new AccountId(new UUID(0, 2)));
        assertThrows(NullPointerException.class, () -> new AccountId(null));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 1999, 2000, Long.MAX_VALUE})
    void wholeUnitBoundaries(long units) {
        assertEquals(units, new Balance(units).units());
        assertEquals(new Balance(units), new Balance(units));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void negativeValuesRejected(long units) {
        assertThrows(IllegalArgumentException.class, () -> new Balance(units));
    }

    @Test void productNameDoesNotRenameCurrency() {
        assertEquals("scrip", Scrip.MOD_ID);
        assertEquals("$crip", Scrip.DISPLAY_NAME);
        assertEquals("Coins", Scrip.DEFAULT_CURRENCY);
    }
}

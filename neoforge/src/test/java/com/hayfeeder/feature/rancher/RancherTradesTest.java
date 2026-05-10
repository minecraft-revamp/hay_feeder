package com.hayfeeder.feature.rancher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the shape of the rancher's code-side trade manifest in
 * {@link RancherTrades}. Trades live as datapack JSON at runtime; this test
 * exists so any drift between the Java manifest and our design intent is
 * caught at build time without firing up a server. The corresponding JSON
 * shape parity is enforced separately by {@code tests/validate.py}.
 */
class RancherTradesTest {

    @Test
    void all_five_levels_are_present() {
        assertEquals(5, RancherTrades.BY_LEVEL.size());
        for (int level = 1; level <= 5; level++) {
            assertNotNull(RancherTrades.BY_LEVEL.get(level),
                    "level " + level + " missing from BY_LEVEL");
        }
    }

    @Test
    void every_level_has_two_to_four_trades() {
        for (var entry : RancherTrades.BY_LEVEL.entrySet()) {
            int level = entry.getKey();
            int count = entry.getValue().size();
            assertTrue(count >= 2 && count <= 4,
                    "level " + level + " has " + count + " trades; expected 2..4");
        }
    }

    @Test
    void no_trade_has_a_zero_count_or_zero_uses() {
        for (var entry : RancherTrades.BY_LEVEL.entrySet()) {
            for (RancherTrades.Trade t : entry.getValue()) {
                assertTrue(t.wantCount() > 0, "wantCount must be > 0: " + t);
                assertTrue(t.giveCount() > 0, "giveCount must be > 0: " + t);
                assertTrue(t.maxUses() > 0, "maxUses must be > 0: " + t);
                assertTrue(t.xp() >= 0, "xp must be >= 0: " + t);
            }
        }
    }

    @Test
    void level_1_buys_feed_and_sells_eggs() {
        List<RancherTrades.Trade> l1 = RancherTrades.LEVEL_1;
        // Wheat / carrot buys exist
        assertTrue(l1.stream().anyMatch(t -> t.want().toString().contains("wheat")),
                "level 1 must accept wheat");
        assertTrue(l1.stream().anyMatch(t -> t.want().toString().contains("carrot")),
                "level 1 must accept carrots");
    }

    @Test
    void master_level_offers_saddle_and_name_tag() {
        List<RancherTrades.Trade> l5 = RancherTrades.LEVEL_5;
        assertTrue(l5.stream().anyMatch(t -> t.give().toString().contains("saddle")),
                "master rancher must sell a saddle");
        assertTrue(l5.stream().anyMatch(t -> t.give().toString().contains("name_tag")),
                "master rancher must sell a name tag");
    }

    @Test
    void rancher_never_sells_meat_or_kills_animals() {
        // The whole point of the rancher: sustainable animal products only.
        String[] forbidden = {"beef", "porkchop", "mutton", "chicken", "rabbit"};
        for (var entry : RancherTrades.BY_LEVEL.entrySet()) {
            for (RancherTrades.Trade t : entry.getValue()) {
                String give = t.give().toString();
                for (String f : forbidden) {
                    assertFalse(give.contains(f),
                            "rancher must not sell meat (" + f + "), found in " + t);
                }
            }
        }
    }
}

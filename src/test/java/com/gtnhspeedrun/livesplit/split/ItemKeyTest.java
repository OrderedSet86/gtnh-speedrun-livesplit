package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** An item key reads back to the same text, and each malformed key is rejected with the expected form. */
class ItemKeyTest {

    @ParameterizedTest
    @ValueSource(
        strings = { "minecraft:netherrack@0", "gregtech:gt.blockmachines@1000", "GalacticraftCore:tile.moonBlock@4",
            "a:b@32767", "IC2:itemCable@13" })
    void keyReadsBackToTheSameText(String text) {
        assertEquals(
            text,
            ItemKey.parse(text)
                .format());
    }

    @Test
    void keyHasItsParts() {
        assertEquals(new ItemKey("gregtech:gt.blockmachines", 236), ItemKey.parse("gregtech:gt.blockmachines@236"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = { "", "netherrack@0", "minecraft:netherrack", "minecraft:netherrack@", "minecraft:netherrack@-1",
            "minecraft:netherrack@32768", "minecraft:netherrack@123456", "minecraft:nether rack@0", "minecraft:@0",
            ":netherrack@0", "a:b@1@2", "a:b@x", "a\u0001:b@0" })
    void malformedKeyIsRejectedWithTheExpectedForm(String text) {
        final IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ItemKey.parse(text));

        assertTrue(
            exception.getMessage()
                .contains("expected"),
            exception.getMessage());
    }

    @Test
    void tooLongRegistryNameIsRejected() {
        final String longName = "a:" + "b".repeat(ItemKey.MAX_REGISTRY_NAME_LENGTH);

        assertThrows(IllegalArgumentException.class, () -> new ItemKey(longName, 0));
    }
}

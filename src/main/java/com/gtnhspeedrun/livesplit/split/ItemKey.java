package com.gtnhspeedrun.livesplit.split;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One item of the item registry, with its metadata, written {@code modid:name@meta}. The splits file names each icon
 * this way, and the client draws the icon from its own copy of the item.
 *
 * <pre>
 *     gregtech:gt.blockmachines@1000    the Electric Blast Furnace controller
 *     minecraft:netherrack@0
 * </pre>
 */
public record ItemKey(String registryName, int meta) {

    public static final int MAX_META = 32_767;
    public static final int MAX_REGISTRY_NAME_LENGTH = 200;
    public static final String EXPECTED_FORM = "modid:name@meta such as \"minecraft:netherrack@0\"";

    // matches:
    // `minecraft:netherrack`
    // `GalacticraftCore:tile.moonBlock`
    private static final String REGISTRY_NAME_REGEX = "[^\\s\\p{Cntrl}:@]+:[^\\s\\p{Cntrl}@]+";
    private static final Pattern REGISTRY_NAME = Pattern.compile(REGISTRY_NAME_REGEX);
    // matches:
    // `minecraft:netherrack@0`
    // `GalacticraftCore:tile.moonBlock@4`
    private static final Pattern KEY_TEXT = Pattern.compile("(" + REGISTRY_NAME_REGEX + ")@(\\d{1,5})");

    public ItemKey {
        if (registryName.length() > MAX_REGISTRY_NAME_LENGTH || !REGISTRY_NAME.matcher(registryName)
            .matches()) {
            throw new IllegalArgumentException(
                "item '" + registryName + "' is not a registry name (expected " + EXPECTED_FORM + ")");
        }
        if (meta < 0 || meta > MAX_META) {
            throw new IllegalArgumentException(
                "item meta " + meta + " is out of range (expected 0 to " + MAX_META + ")");
        }
    }

    /** Read {@code modid:name@meta}, or throw an IllegalArgumentException that names the expected form. */
    public static ItemKey parse(String text) {
        final Matcher matcher = KEY_TEXT.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("item '" + text + "' can't be read (expected " + EXPECTED_FORM + ")");
        }
        return new ItemKey(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    public String format() {
        return registryName + "@" + meta;
    }
}

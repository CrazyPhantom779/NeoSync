package com.breakinblocks.neosync.common.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IdentifierUtil {
    private static final Pattern FIRST_LETTER = Pattern.compile("^\\w|_\\w");

    public static String prettify(ResourceLocation identifier) {
        Matcher matcher = FIRST_LETTER.matcher(identifier.getPath());
        return matcher.replaceAll(x -> {
            String value = x.group(0);
            return value.length() == 1 ? value.toUpperCase() : value.toUpperCase().replace('_', ' ');
        });
    }

    public static Component prettifyAsText(ResourceLocation identifier) {
        String translationKey = "dimension." + identifier.getNamespace() + "." + identifier.getPath();
        return Component.translatableWithFallback(translationKey, prettify(identifier));
    }
}

package me.foesio.foKoTH.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextUtil {

    private TextUtil() {
    }

    public static String normalizePrompt(String input) {
        if (input == null) {
            return "";
        }
        String text = input.replaceAll("(?i)&#[0-9a-f]{6}", "");
        text = text.replaceAll("(?i)(?:&|§)x(?:&|§)[0-9a-f](?:&|§)[0-9a-f](?:&|§)[0-9a-f](?:&|§)[0-9a-f](?:&|§)[0-9a-f](?:&|§)[0-9a-f]", "");
        text = text.replaceAll("(?i)(?:&|§)[0-9a-fk-or]", "");
        text = text.replaceAll("<[^>]{1,32}>", "");
        text = text.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return text.replaceAll("\\p{Cntrl}", "").trim().replaceAll("\\s+", " ");
    }

    public static boolean validId(String input) {
        return input != null && input.matches("[A-Za-z0-9_-]{1,32}");
    }

    public static String id(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }
}

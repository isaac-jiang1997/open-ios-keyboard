package dev.openkeyboard.applelayout;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PinyinEngine {
    private static final String DICTIONARY_ASSET = "pinyin_zh.tsv";
    private static final String RIME_PINYIN_ASSET = "rime/cn_dicts/8105.dict.yaml";
    private static final String ABBREVIATION_ASSET = "t9_abbrev_zh.tsv";
    private static final int MAX_CANDIDATES = 80;
    private static final int MAX_ABBREVIATION_CANDIDATES_PER_KEY = 24;
    private static final int MIN_ABBREVIATION_SYLLABLES = 2;
    private static final int MAX_ABBREVIATION_SYLLABLES = 8;

    private final Map<String, List<String>> dictionary = new HashMap<>();
    private final Map<String, List<String>> reversePinyin = new HashMap<>();
    private final Map<String, List<String>> rimeSingleSyllableCandidates = new HashMap<>();
    private final Map<String, List<String>> abbreviationDictionary = new HashMap<>();
    private final UserDictionaryStore userDictionaryStore;

    PinyinEngine(Context context) {
        userDictionaryStore = new UserDictionaryStore(context);
        load(context);
        loadRimePinyinLookup(context);
        loadRimeAbbreviationLookup(context);
    }

    List<String> candidates(String rawInput) {
        return candidates(rawInput, true);
    }

    List<String> candidates(String rawInput, boolean personalized) {
        String input = normalize(rawInput);
        if (input.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> exact = mergedCandidatesFor(input, personalized);
        if (!exact.isEmpty()) {
            return exact;
        }

        List<String> result = new ArrayList<>();
        if (personalized) {
            for (String pinyin : userDictionaryStore.customPinyinKeysStartingWith(input)) {
                appendUnique(result, userDictionaryStore.customPhrasesFor(pinyin));
                if (result.size() >= MAX_CANDIDATES) {
                    return result.subList(0, MAX_CANDIDATES);
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            if (entry.getKey().startsWith(input)) {
                appendUnique(result, entry.getValue());
                if (result.size() >= MAX_CANDIDATES) {
                    return result.subList(0, MAX_CANDIDATES);
                }
            }
        }
        return result;
    }

    List<String> exactCandidates(String rawInput) {
        String input = normalize(rawInput);
        if (input.isEmpty()) {
            return Collections.emptyList();
        }
        return mergedCandidatesFor(input);
    }

    List<String> exactCandidatesWithRimeFallback(String rawInput) {
        return exactCandidatesWithRimeFallback(rawInput, true);
    }

    List<String> exactCandidatesWithRimeFallback(String rawInput, boolean personalized) {
        String input = normalize(rawInput);
        if (input.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        appendUnique(result, mergedCandidatesFor(input, personalized));
        appendUnique(result, promotedCommonCandidates(input));
        List<String> rime = rimeSingleSyllableCandidates.get(input);
        if (rime != null) {
            appendUnique(result, rime);
        }
        if (personalized) {
            result = prioritizeBySelectionFrequency(input, result);
        }
        if (result.size() > MAX_CANDIDATES) {
            return Collections.unmodifiableList(new ArrayList<>(result.subList(0, MAX_CANDIDATES)));
        }
        return Collections.unmodifiableList(result);
    }

    List<String> candidatesForNineKey(String rawDigits) {
        return candidatesForNineKey(rawDigits, true);
    }

    List<String> candidatesForNineKey(String rawDigits, boolean personalized) {
        String digits = normalizeDigits(rawDigits);
        if (digits.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        if (personalized) {
            for (String pinyin : userDictionaryStore.customPinyinKeysStartingWith("")) {
                if (nineKeySequenceFor(pinyin).startsWith(digits)) {
                    appendUnique(result, userDictionaryStore.customPhrasesFor(pinyin));
                    if (result.size() >= MAX_CANDIDATES) {
                        return result.subList(0, MAX_CANDIDATES);
                    }
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            if (nineKeySequenceFor(entry.getKey()).startsWith(digits)) {
                appendUnique(result, entry.getValue());
                if (result.size() >= MAX_CANDIDATES) {
                    return result.subList(0, MAX_CANDIDATES);
                }
            }
        }
        return result;
    }

    List<String> abbreviationCandidatesForNineKey(String rawDigits, boolean personalized) {
        String digits = normalizeDigits(rawDigits);
        if (digits.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String key : T9AbbreviationDecoder.abbreviationKeysForDigits(digits)) {
            List<String> candidates = abbreviationDictionary.get(key);
            if (candidates != null) {
                appendUnique(result, candidates);
            }
            if (result.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        if (personalized) {
            result = new ArrayList<>(prioritizeBySelectionFrequency(
                    T9AbbreviationDecoder.preferredDisplayForDigits(digits),
                    result));
        }
        if (result.size() > MAX_CANDIDATES) {
            return Collections.unmodifiableList(new ArrayList<>(result.subList(0, MAX_CANDIDATES)));
        }
        return Collections.unmodifiableList(result);
    }

    String pinyinForCandidateMatchingAbbreviationDigits(String candidate, String digits) {
        String normalizedDigits = normalizeDigits(digits);
        if (candidate == null || candidate.isEmpty() || normalizedDigits.isEmpty()) {
            return "";
        }
        List<String> direct = reversePinyin.get(candidate);
        if (direct == null) {
            return "";
        }
        for (String pinyin : direct) {
            String abbreviation = abbreviationKeyForPinyinWithSpaces(pinyin);
            if (!abbreviation.isEmpty()
                    && normalizedDigits.equals(T9AbbreviationDecoder.digitsForAbbreviation(abbreviation))) {
                return pinyin;
            }
        }
        return "";
    }

    void recordSelection(String rawPinyin, String candidate) {
        userDictionaryStore.recordSelection(rawPinyin, candidate);
    }

    String pinyinForCandidateMatchingDigits(String candidate, String digits) {
        String normalizedDigits = normalizeDigits(digits);
        if (candidate == null || candidate.isEmpty() || normalizedDigits.isEmpty()) {
            return "";
        }
        List<String> direct = reversePinyin.get(candidate);
        String directMatch = firstMatchingPinyin(direct, normalizedDigits);
        if (!directMatch.isEmpty()) {
            return directMatch;
        }
        List<String> segmented = new ArrayList<>();
        if (buildPinyinForCodePoints(candidate, 0, normalizedDigits, 0, segmented)) {
            return joinPinyin(segmented);
        }
        return "";
    }

    String pinyinPrefixForCandidateMatchingDigits(String candidate, String digits) {
        String normalizedDigits = normalizeDigits(digits);
        if (candidate == null || candidate.isEmpty() || normalizedDigits.isEmpty()) {
            return "";
        }
        List<String> direct = reversePinyin.get(candidate);
        String directMatch = firstPinyinPrefixMatchingDigits(direct, normalizedDigits);
        if (!directMatch.isEmpty()) {
            return directMatch;
        }
        List<String> segmented = new ArrayList<>();
        if (buildPinyinPrefixForCodePoints(candidate, 0, normalizedDigits, 0, segmented)) {
            return joinPinyin(segmented);
        }
        return "";
    }

    String normalize(String rawInput) {
        return userDictionaryStore.normalizePinyin(rawInput);
    }

    private List<String> mergedCandidatesFor(String pinyin) {
        return mergedCandidatesFor(pinyin, true);
    }

    private List<String> mergedCandidatesFor(String pinyin, boolean personalized) {
        List<String> result = new ArrayList<>();
        if (personalized) {
            appendUnique(result, userDictionaryStore.customPhrasesFor(pinyin));
        }
        List<String> base = dictionary.get(pinyin);
        if (base != null) {
            appendUnique(result, base);
        }
        if (personalized) {
            result = new ArrayList<>(prioritizeBySelectionFrequency(pinyin, result));
        }
        if (result.size() > MAX_CANDIDATES) {
            return result.subList(0, MAX_CANDIDATES);
        }
        return result;
    }

    List<String> prioritizeBySelectionFrequency(String rawPinyin, List<String> candidates) {
        String pinyin = normalize(rawPinyin);
        if (pinyin.isEmpty() || candidates == null || candidates.isEmpty()) {
            return candidates == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            int frequency = Integer.compare(
                    userDictionaryStore.selectionCount(pinyin, b),
                    userDictionaryStore.selectionCount(pinyin, a));
            if (frequency != 0) {
                return frequency;
            }
            return 0;
        });
        return Collections.unmodifiableList(sorted);
    }

    private void appendUnique(List<String> result, List<String> values) {
        for (String value : values) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }
    }

    private String normalizeDigits(String rawDigits) {
        if (rawDigits == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(rawDigits.length());
        for (int i = 0; i < rawDigits.length(); i++) {
            char c = rawDigits.charAt(i);
            if (c >= '2' && c <= '9') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private String nineKeySequenceFor(String pinyin) {
        String normalized = normalize(pinyin);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 'a' && c <= 'c') {
                out.append('2');
            } else if (c >= 'd' && c <= 'f') {
                out.append('3');
            } else if (c >= 'g' && c <= 'i') {
                out.append('4');
            } else if (c >= 'j' && c <= 'l') {
                out.append('5');
            } else if (c >= 'm' && c <= 'o') {
                out.append('6');
            } else if (c >= 'p' && c <= 's') {
                out.append('7');
            } else if (c >= 't' && c <= 'v') {
                out.append('8');
            } else if (c >= 'w' && c <= 'z') {
                out.append('9');
            }
        }
        return out.toString();
    }

    private void load(Context context) {
        try (InputStream input = context.getAssets().open(DICTIONARY_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line);
            }
        } catch (IOException ignored) {
        }
    }

    private void parseLine(String line) {
        if (line.isEmpty() || line.charAt(0) == '#') {
            return;
        }
        String[] parts = line.split("\\t", 2);
        if (parts.length != 2) {
            return;
        }
        String key = normalize(parts[0]);
        if (key.isEmpty()) {
            return;
        }
        String[] values = parts[1].trim().split("\\s+");
        List<String> candidates = new ArrayList<>();
        for (String value : values) {
            if (!value.isEmpty()) {
                candidates.add(value);
            }
        }
        if (!candidates.isEmpty()) {
            dictionary.put(key, Collections.unmodifiableList(candidates));
        }
    }

    private void loadRimePinyinLookup(Context context) {
        try (InputStream input = context.getAssets().open(RIME_PINYIN_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseRimePinyinLine(line);
            }
        } catch (IOException ignored) {
        }
    }

    private void loadRimeAbbreviationLookup(Context context) {
        try (InputStream input = context.getAssets().open(ABBREVIATION_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseAbbreviationIndexLine(line);
            }
        } catch (IOException ignored) {
        }
    }

    private void parseRimePinyinLine(String line) {
        if (line.isEmpty() || line.charAt(0) == '#') {
            return;
        }
        String[] parts = line.split("\\t");
        if (parts.length < 2) {
            return;
        }
        String text = parts[0].trim();
        String pinyin = normalizePinyinWithSpaces(parts[1]);
        if (text.isEmpty() || pinyin.isEmpty()) {
            return;
        }
        addRimeSingleSyllableCandidate(pinyin, text);
        List<String> existing = reversePinyin.get(text);
        if (existing == null) {
            existing = new ArrayList<>();
            reversePinyin.put(text, existing);
        }
        if (!existing.contains(pinyin)) {
            existing.add(pinyin);
        }
    }

    private void parseAbbreviationIndexLine(String line) {
        if (line.isEmpty() || line.charAt(0) == '#') {
            return;
        }
        String[] parts = line.split("\\t");
        if (parts.length < 3) {
            return;
        }
        String abbreviation = normalizeAbbreviationKey(parts[0]);
        String text = parts[1].trim();
        String pinyin = normalizePinyinWithSpaces(parts[2]);
        if (abbreviation.isEmpty() || text.isEmpty() || pinyin.isEmpty()) {
            return;
        }
        if (!abbreviation.equals(abbreviationKeyForPinyinWithSpaces(pinyin))) {
            return;
        }
        addReversePinyin(text, pinyin);
        List<String> candidates = abbreviationDictionary.get(abbreviation);
        if (candidates == null) {
            candidates = new ArrayList<>();
            abbreviationDictionary.put(abbreviation, candidates);
        }
        if (candidates.size() < MAX_ABBREVIATION_CANDIDATES_PER_KEY
                && !candidates.contains(text)) {
            candidates.add(text);
        }
    }

    private void addReversePinyin(String text, String pinyin) {
        List<String> existing = reversePinyin.get(text);
        if (existing == null) {
            existing = new ArrayList<>();
            reversePinyin.put(text, existing);
        }
        if (!existing.contains(pinyin)) {
            existing.add(pinyin);
        }
    }

    private void addRimeSingleSyllableCandidate(String pinyin, String text) {
        if (pinyin.indexOf(' ') >= 0 || text.codePointCount(0, text.length()) != 1) {
            return;
        }
        int codePoint = text.codePointAt(0);
        if (codePoint < 0x4E00 || codePoint > 0x9FFF) {
            return;
        }
        List<String> existing = rimeSingleSyllableCandidates.get(pinyin);
        if (existing == null) {
            existing = new ArrayList<>();
            rimeSingleSyllableCandidates.put(pinyin, existing);
        }
        if (!existing.contains(text)) {
            existing.add(text);
        }
    }

    private List<String> promotedCommonCandidates(String pinyin) {
        if ("pu".equals(pinyin)) {
            return java.util.Arrays.asList("普", "铺", "扑", "谱", "浦", "蒲", "仆", "朴", "埔", "菩", "葡", "瀑");
        }
        if ("qu".equals(pinyin)) {
            return java.util.Arrays.asList("去", "取", "区", "曲", "趣", "渠", "屈", "驱", "趋", "娶", "祛", "躯");
        }
        return Collections.emptyList();
    }

    private String normalizePinyinWithSpaces(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String lower = rawInput.toLowerCase(java.util.Locale.US);
        StringBuilder out = new StringBuilder(lower.length());
        boolean previousSpace = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append(c);
                previousSpace = false;
            } else if ((c == ' ' || c == '\'') && !previousSpace) {
                out.append(' ');
                previousSpace = true;
            }
        }
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') {
            out.deleteCharAt(length - 1);
        }
        return out.toString();
    }

    private String abbreviationKeyForPinyinWithSpaces(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) {
            return "";
        }
        String[] syllables = pinyin.split(" ");
        if (syllables.length < MIN_ABBREVIATION_SYLLABLES
                || syllables.length > MAX_ABBREVIATION_SYLLABLES) {
            return "";
        }
        StringBuilder out = new StringBuilder(syllables.length);
        for (String syllable : syllables) {
            if (syllable.isEmpty()) {
                return "";
            }
            char initial = syllable.charAt(0);
            if (initial < 'a' || initial > 'z') {
                return "";
            }
            out.append(initial);
        }
        return out.toString();
    }

    private String normalizeAbbreviationKey(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        String lower = rawKey.toLowerCase(java.util.Locale.US);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private String firstMatchingPinyin(List<String> pinyins, String digits) {
        if (pinyins == null) {
            return "";
        }
        for (String pinyin : pinyins) {
            if (digits.equals(nineKeySequenceFor(pinyin))) {
                return pinyin;
            }
        }
        return "";
    }

    private String firstPinyinPrefixMatchingDigits(List<String> pinyins, String digits) {
        if (pinyins == null) {
            return "";
        }
        for (String pinyin : pinyins) {
            String pinyinDigits = nineKeySequenceFor(pinyin);
            if (pinyinDigits.startsWith(digits)) {
                return pinyinPrefixForDigitLength(pinyin, digits.length());
            }
        }
        return "";
    }

    private boolean buildPinyinForCodePoints(
            String text,
            int charIndex,
            String digits,
            int digitIndex,
            List<String> out
    ) {
        if (charIndex >= text.length()) {
            return digitIndex == digits.length();
        }
        int codePoint = text.codePointAt(charIndex);
        String character = new String(Character.toChars(codePoint));
        List<String> pinyins = reversePinyin.get(character);
        if (pinyins == null || pinyins.isEmpty()) {
            return false;
        }
        for (String pinyin : pinyins) {
            String pyDigits = nineKeySequenceFor(pinyin);
            if (pyDigits.isEmpty() || !digits.startsWith(pyDigits, digitIndex)) {
                continue;
            }
            out.add(pinyin);
            if (buildPinyinForCodePoints(
                    text,
                    charIndex + Character.charCount(codePoint),
                    digits,
                    digitIndex + pyDigits.length(),
                    out)) {
                return true;
            }
            out.remove(out.size() - 1);
        }
        return false;
    }

    private boolean buildPinyinPrefixForCodePoints(
            String text,
            int charIndex,
            String digits,
            int digitIndex,
            List<String> out
    ) {
        if (charIndex >= text.length()) {
            return digitIndex == digits.length();
        }
        int codePoint = text.codePointAt(charIndex);
        String character = new String(Character.toChars(codePoint));
        List<String> pinyins = reversePinyin.get(character);
        if (pinyins == null || pinyins.isEmpty()) {
            return false;
        }
        for (String pinyin : pinyins) {
            String pyDigits = nineKeySequenceFor(pinyin);
            if (pyDigits.isEmpty()) {
                continue;
            }
            int remaining = digits.length() - digitIndex;
            if (remaining <= 0) {
                return true;
            }
            if (remaining < pyDigits.length()) {
                String remainderDigits = digits.substring(digitIndex);
                if (pyDigits.startsWith(remainderDigits)) {
                    out.add(pinyinPrefixForDigitLength(pinyin, remaining));
                    return true;
                }
                continue;
            }
            if (!digits.startsWith(pyDigits, digitIndex)) {
                continue;
            }
            out.add(pinyin);
            if (buildPinyinPrefixForCodePoints(
                    text,
                    charIndex + Character.charCount(codePoint),
                    digits,
                    digitIndex + pyDigits.length(),
                    out)) {
                return true;
            }
            out.remove(out.size() - 1);
        }
        return false;
    }

    private String pinyinPrefixForDigitLength(String pinyin, int digitLength) {
        if (pinyin == null || digitLength <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int consumed = 0;
        for (int i = 0; i < pinyin.length(); i++) {
            char c = pinyin.charAt(i);
            if (c == ' ') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(c);
                }
                continue;
            }
            out.append(c);
            consumed++;
            if (consumed >= digitLength) {
                break;
            }
        }
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') {
            out.deleteCharAt(length - 1);
        }
        return out.toString();
    }

    private String joinPinyin(List<String> pinyins) {
        StringBuilder builder = new StringBuilder();
        for (String pinyin : pinyins) {
            if (pinyin == null || pinyin.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(pinyin);
        }
        return builder.toString();
    }

}

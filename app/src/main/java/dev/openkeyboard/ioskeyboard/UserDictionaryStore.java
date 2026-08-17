package dev.openkeyboard.ioskeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class UserDictionaryStore {
    private static final String PREFS_NAME = "local_user_dictionary";
    private static final String PHRASE_PREFIX = "phrase:";
    private static final String FREQUENCY_PREFIX = "frequency:";
    private static final int MAX_SELECTION_COUNT = 10000;
    private static final int FREQUENCY_DECAY_DIVISOR = 2;

    private final SharedPreferences preferences;

    UserDictionaryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        removeLegacyFrequencyKeys();
    }

    void addCustomPhrase(String rawPinyin, String rawPhrase) {
        String pinyin = normalizePinyin(rawPinyin);
        String phrase = rawPhrase == null ? "" : rawPhrase.trim();
        if (pinyin.isEmpty() || phrase.isEmpty()) {
            return;
        }
        Set<String> phrases = new HashSet<>(preferences.getStringSet(PHRASE_PREFIX + pinyin, Collections.emptySet()));
        phrases.add(phrase);
        preferences.edit().putStringSet(PHRASE_PREFIX + pinyin, phrases).apply();
    }

    List<String> customPhrasesFor(String rawPinyin) {
        String pinyin = normalizePinyin(rawPinyin);
        if (pinyin.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> phrases = preferences.getStringSet(PHRASE_PREFIX + pinyin, Collections.emptySet());
        List<String> result = new ArrayList<>(phrases);
        result.sort((a, b) -> {
            int frequency = Integer.compare(selectionCount(pinyin, b), selectionCount(pinyin, a));
            return frequency != 0 ? frequency : a.compareTo(b);
        });
        return result;
    }

    List<String> customPinyinKeysStartingWith(String rawPrefix) {
        String prefix = normalizePinyin(rawPrefix);
        List<String> result = new ArrayList<>();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(PHRASE_PREFIX)) {
                String pinyin = key.substring(PHRASE_PREFIX.length());
                if (pinyin.startsWith(prefix)) {
                    result.add(pinyin);
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    void recordSelection(String rawPinyin, String candidate) {
        String pinyin = normalizePinyin(rawPinyin);
        if (pinyin.isEmpty() || candidate == null || candidate.isEmpty()) {
            return;
        }
        String key = frequencyKey(pinyin, candidate);
        String legacyKey = legacyFrequencyKey(pinyin, candidate);
        int current = Math.max(preferences.getInt(key, 0), preferences.getInt(legacyKey, 0));
        SharedPreferences.Editor editor = preferences.edit().remove(legacyKey);
        if (current >= MAX_SELECTION_COUNT) {
            current = decayFrequenciesForPinyin(editor, preferences.getAll(), pinyin, key, current);
        }
        editor.putInt(key, Math.min(MAX_SELECTION_COUNT, current + 1)).apply();
    }

    int selectionCount(String rawPinyin, String candidate) {
        String pinyin = normalizePinyin(rawPinyin);
        if (pinyin.isEmpty() || candidate == null || candidate.isEmpty()) {
            return 0;
        }
        return preferences.getInt(frequencyKey(pinyin, candidate), 0);
    }

    List<String> describeCustomPhrases() {
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(PHRASE_PREFIX) || !(entry.getValue() instanceof Set)) {
                continue;
            }
            String pinyin = key.substring(PHRASE_PREFIX.length());
            Set<?> values = (Set<?>) entry.getValue();
            for (Object value : values) {
                if (value instanceof String) {
                    rows.add(pinyin + " -> " + value);
                }
            }
        }
        Collections.sort(rows);
        return rows;
    }

    boolean clearAll() {
        return preferences.edit().clear().commit();
    }

    String normalizePinyin(String rawPinyin) {
        if (rawPinyin == null) {
            return "";
        }
        String lower = rawPinyin.toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || c == '\'') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private String frequencyKey(String pinyin, String candidate) {
        return FREQUENCY_PREFIX + pinyin + ":" + sha256Hex(candidate);
    }

    private int decayFrequenciesForPinyin(
            SharedPreferences.Editor editor,
            Map<String, ?> allPreferences,
            String pinyin,
            String selectedKey,
            int selectedCurrent) {
        String prefix = FREQUENCY_PREFIX + pinyin + ":";
        int selectedAfterDecay = Math.max(1, selectedCurrent / FREQUENCY_DECAY_DIVISOR);
        for (Map.Entry<String, ?> entry : allPreferences.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!key.startsWith(prefix) || !(value instanceof Integer)) {
                continue;
            }
            int decayed = Math.max(1, ((Integer) value) / FREQUENCY_DECAY_DIVISOR);
            editor.putInt(key, decayed);
            if (key.equals(selectedKey)) {
                selectedAfterDecay = decayed;
            }
        }
        return selectedAfterDecay;
    }

    private String legacyFrequencyKey(String pinyin, String candidate) {
        return FREQUENCY_PREFIX + pinyin + ":" + Integer.toHexString(candidate.hashCode()) + ":" + candidate;
    }

    private void removeLegacyFrequencyKeys() {
        SharedPreferences.Editor editor = null;
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(FREQUENCY_PREFIX) && !isCurrentFrequencyKey(key)) {
                if (editor == null) {
                    editor = preferences.edit();
                }
                editor.remove(key);
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private boolean isCurrentFrequencyKey(String key) {
        int pinyinEnd = key.indexOf(':', FREQUENCY_PREFIX.length());
        if (pinyinEnd < 0) {
            return false;
        }
        String digest = key.substring(pinyinEnd + 1);
        if (digest.length() != 64) {
            return false;
        }
        for (int i = 0; i < digest.length(); i++) {
            char c = digest.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}

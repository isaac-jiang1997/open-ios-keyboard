package dev.openkeyboard.ioskeyboard;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RimeNativeBridge {
    private static final char CANDIDATE_SEPARATOR = '\u001f';
    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("openphone_rime");
            loaded = true;
        } catch (UnsatisfiedLinkError ignored) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private RimeNativeBridge() {
    }

    static boolean isLibraryLoaded() {
        return LIBRARY_LOADED;
    }

    static boolean initialize(File sharedDir, File userDir) {
        return LIBRARY_LOADED
                && nativeInitialize(sharedDir.getAbsolutePath(), userDir.getAbsolutePath());
    }

    static Snapshot selectSchema(String schemaId) {
        return fromArray(nativeSelectSchema(schemaId));
    }

    static Snapshot processKey(int keyCode) {
        return fromArray(nativeProcessKey(keyCode));
    }

    static Snapshot selectCandidate(int index) {
        return fromArray(nativeSelectCandidate(index));
    }

    static Snapshot commitComposition() {
        return fromArray(nativeCommitComposition());
    }

    static Snapshot clear() {
        return fromArray(nativeClear());
    }

    static Snapshot snapshot() {
        return fromArray(nativeSnapshot());
    }

    static void finalizeEngine() {
        if (LIBRARY_LOADED) {
            nativeFinalize();
        }
    }

    private static Snapshot fromArray(String[] values) {
        if (values == null || values.length == 0) {
            return Snapshot.EMPTY;
        }
        String commit = values[0] == null ? "" : values[0];
        String preedit = values.length > 1 && values[1] != null ? values[1] : "";
        List<String> candidates = new ArrayList<>();
        List<String> candidateAnnotations = new ArrayList<>();
        for (int i = 2; i < values.length; i++) {
            if (values[i] != null && !values[i].isEmpty()) {
                String value = values[i];
                int separator = value.indexOf(CANDIDATE_SEPARATOR);
                if (separator >= 0) {
                    candidates.add(value.substring(0, separator));
                    candidateAnnotations.add(value.substring(separator + 1));
                } else {
                    candidates.add(value);
                    candidateAnnotations.add("");
                }
            }
        }
        return new Snapshot(commit, preedit, candidates, candidateAnnotations);
    }

    private static native boolean nativeInitialize(String sharedDir, String userDir);

    private static native String[] nativeSelectSchema(String schemaId);

    private static native String[] nativeProcessKey(int keyCode);

    private static native String[] nativeSelectCandidate(int index);

    private static native String[] nativeCommitComposition();

    private static native String[] nativeClear();

    private static native String[] nativeSnapshot();

    private static native void nativeFinalize();

    static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot("", "", Collections.emptyList(), Collections.emptyList());

        final String commit;
        final String preedit;
        final List<String> candidates;
        final List<String> candidateAnnotations;

        Snapshot(String commit, String preedit, List<String> candidates, List<String> candidateAnnotations) {
            this.commit = commit == null ? "" : commit;
            this.preedit = preedit == null ? "" : preedit;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.candidateAnnotations = Collections.unmodifiableList(new ArrayList<>(candidateAnnotations));
        }
    }
}

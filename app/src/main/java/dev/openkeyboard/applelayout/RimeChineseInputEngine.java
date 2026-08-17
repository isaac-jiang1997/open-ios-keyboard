package dev.openkeyboard.applelayout;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class RimeChineseInputEngine implements ChineseInputEngine {
    private static final String ASSET_VERSION = "rime-assets-2026-05-18.1-rime-ice-t9-abbrev";
    private static final String SCHEMA_PINYIN = "openphone_pinyin";
    private static final String SCHEMA_T9 = "openphone_t9";
    private static final int KEY_BACKSPACE = 0xff08;

    private RimeNativeBridge.Snapshot snapshot = RimeNativeBridge.Snapshot.EMPTY;
    private ChineseKeyboardLayout layout = ChineseKeyboardLayout.NINE_KEY;
    private String activeSchema = "";
    private String pendingCommit = "";
    private boolean learningAllowed = true;

    static RimeChineseInputEngine create(Context context) {
        if (!RimeNativeBridge.isLibraryLoaded()) {
            return null;
        }
        try {
            return new RimeChineseInputEngine(context.getApplicationContext());
        } catch (IOException ignored) {
            return null;
        }
    }

    static boolean clearUserData(Context context) {
        if (context == null) {
            return false;
        }
        if (RimeNativeBridge.isLibraryLoaded()) {
            RimeNativeBridge.finalizeEngine();
        }
        File baseDir = new File(context.getApplicationContext().getFilesDir(), "rime");
        File userDir = new File(baseDir, "user");
        try {
            deleteRecursively(userDir);
            return userDir.exists() || userDir.mkdirs();
        } catch (IOException ignored) {
            return false;
        }
    }

    private RimeChineseInputEngine(Context context) throws IOException {
        File baseDir = new File(context.getFilesDir(), "rime");
        File sharedDir = new File(baseDir, "shared");
        File userDir = new File(baseDir, "user");
        installSharedAssets(context.getAssets(), sharedDir);
        if (!userDir.exists() && !userDir.mkdirs()) {
            throw new IOException("Cannot create Rime user directory");
        }
        if (!RimeNativeBridge.initialize(sharedDir, userDir)) {
            throw new IOException("Cannot initialize librime");
        }
        setLayout(layout);
    }

    @Override
    public void setLayout(ChineseKeyboardLayout layout) {
        this.layout = layout == null ? ChineseKeyboardLayout.NINE_KEY : layout;
        String targetSchema = this.layout == ChineseKeyboardLayout.NINE_KEY ? SCHEMA_T9 : SCHEMA_PINYIN;
        if (!targetSchema.equals(activeSchema)) {
            snapshot = RimeNativeBridge.selectSchema(targetSchema);
            pendingCommit = snapshot.commit;
            activeSchema = targetSchema;
        }
    }

    @Override
    public void setLearningAllowed(boolean allowed) {
        learningAllowed = allowed;
    }

    @Override
    public void reset() {
        snapshot = RimeNativeBridge.clear();
        pendingCommit = "";
    }

    @Override
    public boolean hasComposition() {
        return !snapshot.preedit.isEmpty();
    }

    @Override
    public String preedit() {
        return snapshot.preedit;
    }

    @Override
    public List<String> candidates() {
        return snapshot.candidates;
    }

    @Override
    public List<String> candidateAnnotations() {
        return snapshot.candidateAnnotations;
    }

    @Override
    public void appendAsciiLetter(String letter) {
        if (letter == null || letter.length() != 1) {
            return;
        }
        processKey(letter.toLowerCase(Locale.US).charAt(0));
    }

    @Override
    public void appendNineKeyDigit(String digit) {
        if (digit == null || digit.length() != 1) {
            return;
        }
        char c = digit.charAt(0);
        if (c >= '0' && c <= '9') {
            processKey(c);
        }
    }

    @Override
    public String consumePendingCommit() {
        String commit = pendingCommit;
        pendingCommit = "";
        return commit;
    }

    @Override
    public boolean backspace() {
        if (!hasComposition()) {
            return false;
        }
        processKey(KEY_BACKSPACE);
        return true;
    }

    @Override
    public String commitCandidate(String text) {
        int index = snapshot.candidates.indexOf(text);
        if (index >= 0) {
            if (!learningAllowed) {
                reset();
                return text == null ? "" : text;
            }
            snapshot = RimeNativeBridge.selectCandidate(index);
            String commit = snapshot.commit;
            pendingCommit = "";
            return commit.isEmpty() ? text : commit;
        }
        reset();
        return text == null ? "" : text;
    }

    @Override
    public String commitBestCandidateOrRaw() {
        if (!snapshot.candidates.isEmpty()) {
            if (!learningAllowed) {
                String text = snapshot.candidates.get(0);
                reset();
                return text;
            }
            snapshot = RimeNativeBridge.selectCandidate(0);
            String commit = snapshot.commit;
            pendingCommit = "";
            return commit.isEmpty() ? commitRaw() : commit;
        }
        return commitRaw();
    }

    @Override
    public String commitRaw() {
        String fallback = snapshot.preedit;
        snapshot = RimeNativeBridge.commitComposition();
        String commit = snapshot.commit;
        pendingCommit = "";
        if (!commit.isEmpty()) {
            return commit;
        }
        reset();
        return fallback;
    }

    private void processKey(int keyCode) {
        snapshot = RimeNativeBridge.processKey(keyCode);
        if (!snapshot.commit.isEmpty()) {
            pendingCommit = pendingCommit + snapshot.commit;
        }
    }

    private static void installSharedAssets(AssetManager assets, File sharedDir) throws IOException {
        File versionFile = new File(sharedDir, ".openphone_version");
        if (versionFile.exists()) {
            String existing = readUtf8(versionFile);
            if (ASSET_VERSION.equals(existing.trim())) {
                return;
            }
        }
        deleteRecursively(sharedDir);
        if (!sharedDir.mkdirs()) {
            throw new IOException("Cannot create Rime shared directory");
        }
        copyAssetDirectory(assets, "rime", sharedDir);
        writeUtf8(versionFile, ASSET_VERSION);
    }

    private static void copyAssetDirectory(AssetManager assets, String assetPath, File targetDir)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, targetDir);
            return;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create " + targetDir);
        }
        for (String child : children) {
            String childPath = assetPath + "/" + child;
            File childTarget = new File(targetDir, child);
            String[] grandChildren = assets.list(childPath);
            if (grandChildren != null && grandChildren.length > 0) {
                copyAssetDirectory(assets, childPath, childTarget);
            } else {
                copyAssetFile(assets, childPath, childTarget);
            }
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File targetFile)
            throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(targetFile)) {
            copyStream(input, output);
        }
    }

    private static String readUtf8(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void writeUtf8(File file, String value) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Cannot delete " + file);
        }
    }
}

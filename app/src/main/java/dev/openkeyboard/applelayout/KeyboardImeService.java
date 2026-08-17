package dev.openkeyboard.applelayout;

import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KeyboardImeService extends InputMethodService
        implements IosKeyboardView.Listener, CandidateStripView.Listener,
        ExpandedCandidatePanelView.Listener, IosAccessoryBarView.Listener {
    private static final int CANDIDATE_STRIP_HEIGHT_DP = 64;
    private static final long PUNCTUATION_CYCLE_TIMEOUT_MS = 900L;
    private static final String PREFS_NAME = "keyboard_state";
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_CHINESE_LAYOUT = "chinese_layout";

    private static final List<String> PUNCTUATION_CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            "，", "。", "？", "！", "、", "；", "：", "…", "—", "“", "”", "（", "）", "《", "》"));
    private static final List<String> PUNCTUATION_STRIP_CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            "。", "？", "！", "……", "~", "#", "：", "、", "“”"));
    private static final List<String> PUNCTUATION_PANEL_CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            "。", "？", "！", "……", "~", "#", "：", "、", "“”",
            "”", "\"", "()", "-", "—", "；",
            "@", "*", "_", "～", "%", "&",
            "·", "•", "/", "\\", "《》", "〈〉",
            "「」", "『』", "〔〕", "【】", "[]", "{}",
            "()", "<>", "+", "-", "×", "÷",
            "=", "^", "¥", "$", "£", "€",
            "℃", "℉", "，", "。", "：", "；",
            "？", "！", "|", "丨", "←", "↑",
            "→", "↓", "\"", "'", "…", "￥",
            "$", "£", "+", "−", "/", "\\",
            "=", "°", "*", "#", "@", "%",
            "&", "_"));

    private IosKeyboardView keyboardView;
    private CandidateStripView candidateStripView;
    private ExpandedCandidatePanelView expandedCandidatePanelView;
    private IosAccessoryBarView accessoryBarView;
    private KeyPopupView keyPopupView;
    private ChineseInputEngine chineseInputEngine;
    private PinyinEngine localPinyinEngine;
    private final T9PinyinDecoder t9PinyinDecoder = new T9PinyinDecoder();
    private final StringBuilder t9CompositionTokens = new StringBuilder();
    private final StringBuilder t9SelectedText = new StringBuilder();
    private final StringBuilder t9SelectedPinyinHistory = new StringBuilder();
    private T9PinyinDecoder.Result t9PinyinResult = T9PinyinDecoder.EMPTY;
    private String activePinyinText = "";
    private List<String> activeChineseCandidates = Collections.emptyList();
    private List<String> activeChineseCandidateAnnotations = Collections.emptyList();
    private String selectedT9Pinyin = "";
    private int selectedT9PinyinDigitLength = 0;
    private InputLanguage language = InputLanguage.CHINESE;
    private ChineseKeyboardLayout chineseKeyboardLayout = ChineseKeyboardLayout.NINE_KEY;
    private KeyboardMode keyboardMode = KeyboardMode.LETTERS;
    private boolean shouldAutoShift = true;
    private boolean learningAllowed = true;
    private boolean punctuationPickerVisible;
    private boolean punctuationCycleActive;
    private int punctuationCycleIndex = -1;
    private long lastPunctuationTapMs;
    private String lastPunctuationText = "";
    private boolean candidatePanelExpanded;

    @Override
    public void onCreate() {
        super.onCreate();
        restoreBaseInputMode();
        styleImeSystemBars();
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        return true;
    }

    @Override
    public View onCreateInputView() {
        chineseInputEngine = createChineseInputEngine();
        localPinyinEngine = new PinyinEngine(this);
        styleImeSystemBars();
        chineseInputEngine.setLayout(chineseKeyboardLayout);
        chineseInputEngine.setLearningAllowed(learningAllowed);
        candidateStripView = new CandidateStripView(this);
        candidateStripView.setListener(this);
        expandedCandidatePanelView = new ExpandedCandidatePanelView(this);
        expandedCandidatePanelView.setListener(this);
        expandedCandidatePanelView.setVisibility(View.GONE);

        keyboardView = new IosKeyboardView(this);
        keyboardView.setListener(this);
        keyboardView.setLanguage(language);
        keyboardView.setChineseKeyboardLayout(chineseKeyboardLayout);
        keyboardView.setAutoShift(shouldAutoShift);

        accessoryBarView = new IosAccessoryBarView(this);
        accessoryBarView.setListener(this);

        keyPopupView = new KeyPopupView(this);
        keyboardView.setKeyPopupView(keyPopupView);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(keyboardView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(accessoryBarView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout root = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = View.MeasureSpec.getSize(widthMeasureSpec);
                int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
                int wrapHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                int candidateHeight = candidateStripView.getVisibility() == View.GONE
                        ? 0
                        : dp(CANDIDATE_STRIP_HEIGHT_DP);
                int panelHeight = expandedCandidatePanelView.getVisibility() == View.GONE
                        ? 0
                        : keyboardAreaHeight(width);

                column.measure(widthSpec, wrapHeightSpec);
                candidateStripView.measure(widthSpec,
                        View.MeasureSpec.makeMeasureSpec(candidateHeight, View.MeasureSpec.EXACTLY));
                expandedCandidatePanelView.measure(widthSpec,
                        View.MeasureSpec.makeMeasureSpec(panelHeight, View.MeasureSpec.EXACTLY));
                int desiredHeight = column.getMeasuredHeight() + candidateHeight + panelHeight;
                int maxHeight = View.MeasureSpec.getSize(heightMeasureSpec);
                if (maxHeight > 0) {
                    desiredHeight = Math.min(desiredHeight, maxHeight);
                }

                int exactHeightSpec = View.MeasureSpec.makeMeasureSpec(desiredHeight, View.MeasureSpec.EXACTLY);
                keyPopupView.measure(widthSpec, exactHeightSpec);
                setMeasuredDimension(width, desiredHeight);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int height = bottom - top;
                int columnHeight = column.getMeasuredHeight();
                int candidateHeight = candidateStripView.getMeasuredHeight();
                int panelHeight = expandedCandidatePanelView.getMeasuredHeight();
                candidateStripView.layout(0, 0, width, candidateHeight);
                expandedCandidatePanelView.layout(0, candidateHeight, width, candidateHeight + panelHeight);
                int columnTop = Math.max(candidateHeight + panelHeight, height - columnHeight);
                column.layout(0, columnTop, width, columnTop + columnHeight);
                keyPopupView.layout(0, 0, width, height);
            }
        };
        root.setClipChildren(false);
        root.addView(candidateStripView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(CANDIDATE_STRIP_HEIGHT_DP)));
        root.addView(expandedCandidatePanelView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(keyPopupView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        updateReturnKeyAppearance(getCurrentInputEditorInfo());
        updateCandidates();
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        styleImeSystemBars();
        clearComposition();
        learningAllowed = shouldAllowLearning(attribute);
        if (chineseInputEngine != null) {
            chineseInputEngine.setLearningAllowed(learningAllowed);
        }
        shouldAutoShift = true;
        if (keyboardView != null) {
            resetTransientKeyboardMode();
            keyboardView.setLanguage(language);
            keyboardView.setChineseKeyboardLayout(chineseKeyboardLayout);
            keyboardView.setAutoShift(true);
            keyboardView.setSensitiveInput(!learningAllowed);
            updateReturnKeyAppearance(attribute);
        }
        updateCandidates();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        styleImeSystemBars();
        resetTransientKeyboardMode();
        updateReturnKeyAppearance(info);
        updateCandidates();
    }

    private void updateReturnKeyAppearance(EditorInfo info) {
        if (keyboardView == null) {
            return;
        }
        if (info == null) {
            keyboardView.setReturnKeyAppearance(
                    language == InputLanguage.CHINESE ? "\u6362\u884C" : "return", false);
            return;
        }
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean isAction;
        String label;
        boolean isChinese = language == InputLanguage.CHINESE;
        switch (action) {
            case EditorInfo.IME_ACTION_SEARCH:
                label = isChinese ? "\u641C\u7D22" : "search";
                isAction = true;
                break;
            case EditorInfo.IME_ACTION_GO:
                label = isChinese ? "\u524D\u5F80" : "go";
                isAction = true;
                break;
            case EditorInfo.IME_ACTION_SEND:
                label = isChinese ? "\u53D1\u9001" : "send";
                isAction = true;
                break;
            case EditorInfo.IME_ACTION_NEXT:
                label = isChinese ? "\u4E0B\u4E00\u9879" : "next";
                isAction = true;
                break;
            case EditorInfo.IME_ACTION_DONE:
                label = isChinese ? "\u5B8C\u6210" : "done";
                isAction = true;
                break;
            default:
                label = isChinese ? "\u6362\u884C" : "return";
                isAction = false;
                break;
        }
        keyboardView.setReturnKeyAppearance(label, isAction);
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        clearComposition();
        punctuationPickerVisible = false;
        hideCandidatePanel();
        resetTransientKeyboardMode();
        updateCandidates();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        resetTransientKeyboardMode();
        if (keyPopupView != null) {
            keyPopupView.hide();
        }
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        resetTransientKeyboardMode();
        if (keyPopupView != null) {
            keyPopupView.hide();
        }
    }

    @Override
    public void onKey(KeyboardKey key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }

        switch (key.action) {
            case CHARACTER:
                handleCharacter(ic, key.output);
                break;
            case SHIFT:
                keyboardView.toggleShift();
                break;
            case BACKSPACE:
                onBackspace();
                break;
            case SPACE:
                handleSpace(ic);
                break;
            case RETURN:
                handleReturn(ic);
                break;
            case MODE_123:
                commitPendingComposition(ic);
                if (keyboardMode == KeyboardMode.SYMBOLS_MORE
                        && language == InputLanguage.CHINESE
                        && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
                    setKeyboardMode(KeyboardMode.SYMBOLS);
                } else {
                    setKeyboardMode(KeyboardMode.NUMBERS);
                }
                break;
            case MODE_ABC:
                setKeyboardMode(KeyboardMode.LETTERS);
                keyboardView.setAutoShift(shouldAutoShift);
                break;
            case MODE_SYMBOLS:
                commitPendingComposition(ic);
                if (keyboardMode == KeyboardMode.SYMBOLS
                        && language == InputLanguage.CHINESE
                        && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
                    setKeyboardMode(KeyboardMode.SYMBOLS_MORE);
                } else {
                    setKeyboardMode(KeyboardMode.SYMBOLS);
                }
                break;
            case MODE_EMOJI:
                commitPendingComposition(ic);
                hidePunctuationPicker();
                setKeyboardMode(KeyboardMode.EMOJI);
                keyboardView.setAutoShift(false);
                break;
            case PUNCTUATION_PICKER:
                handlePunctuationCycle(ic);
                break;
            case SEPARATOR:
                handleSeparator(ic);
                break;
            case SELECT_WORDS:
            case CONFIRM_COMPOSITION:
                commitFirstCandidate(ic);
                break;
            case SWITCH_CHINESE_LAYOUT:
                switchChineseKeyboardLayout(ic);
                break;
            case SWITCH_LANGUAGE:
                switchLanguage(ic);
                break;
            case SWITCH_INPUT_METHOD:
                switchInputMethod();
                break;
            case VOICE_INPUT:
                onVoiceInput();
                break;
        }
    }

    @Override
    public void onGlobe() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            cycleInputMode(ic);
        }
    }

    @Override
    public void onVoiceInput() {
        Toast.makeText(this, "离线语音识别尚未内置", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        if (isChineseNineKeyComposing()) {
            if (t9CompositionTokens.length() > 0) {
                boolean removedDigit = removeLastT9Token();
                if (removedDigit && chineseInputEngine != null && chineseInputEngine.hasComposition()) {
                    chineseInputEngine.backspace();
                }
            } else {
                removeLastT9SelectedPinyinHistory();
                removeLastSelectedT9TextCodePoint();
            }
            hidePunctuationPicker();
            syncComposition(ic);
            return;
        }
        if (chineseInputEngine != null && chineseInputEngine.backspace()) {
            hidePunctuationPicker();
            syncComposition(ic);
            return;
        }
        hidePunctuationPicker();
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
        } else {
            deleteOneCodePoint(ic);
        }
        refreshAutoShiftFromContext(ic);
        updateCandidates();
    }

    @Override
    public void onCandidate(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            hideCandidatePanel();
            if (punctuationPickerVisible) {
                commitPunctuationCandidate(ic, text);
            } else if (isChineseNineKeyComposing() && isT9PinyinSelectionActive()) {
                commitSelectedT9SyllableCandidate(ic, text);
            } else {
                commitCandidate(ic, text);
            }
        }
    }

    @Override
    public void onPinyinCandidate(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || text == null || text.isEmpty() || !isChineseNineKeyComposing()) {
            return;
        }
        String pinyin = normalizeT9PinyinCandidate(text);
        int digitLength = leadingDigitLengthForPinyin(pinyin);
        if (pinyin.isEmpty() || digitLength <= 0) {
            return;
        }
        selectedT9Pinyin = pinyin;
        selectedT9PinyinDigitLength = digitLength;
        activePinyinText = displayTextForSelectedPinyin(pinyin, digitLength);
        activeChineseCandidates = candidatesForSelectedPinyin(pinyin);
        activeChineseCandidateAnnotations = repeatAnnotation(activeChineseCandidates.size(), pinyin);
        ic.setComposingText(t9ComposingDisplayText(), 1);
        updateCandidates();
    }

    @Override
    public void onExpandCandidates() {
        if (expandedPanelCandidates().size() <= 6) {
            return;
        }
        candidatePanelExpanded = !candidatePanelExpanded;
        updateExpandedCandidatePanel();
    }

    @Override
    public void onExpandedCandidate(String text) {
        onCandidate(text);
    }

    private void handleCharacter(InputConnection ic, String output) {
        if (punctuationPickerVisible && PUNCTUATION_CANDIDATES.contains(output)) {
            commitPunctuationCandidate(ic, output);
            return;
        }
        hidePunctuationPicker();
        if (language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && keyboardMode == KeyboardMode.LETTERS
                && isNineKeyPinyinDigit(output)) {
            clearSelectedT9Pinyin();
            if (chineseInputEngine != null) {
                chineseInputEngine.setLayout(chineseKeyboardLayout);
                chineseInputEngine.appendNineKeyDigit(output);
            }
            commitPendingEngineText(ic);
            t9CompositionTokens.append(output);
            syncComposition(ic);
            return;
        }
        if (language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && keyboardMode == KeyboardMode.LETTERS
                && "1".equals(output)) {
            commitPendingComposition(ic);
            ic.commitText("，", 1);
            return;
        }
        if (language == InputLanguage.CHINESE && isAsciiLetter(output)) {
            if (chineseInputEngine != null) {
                chineseInputEngine.setLayout(chineseKeyboardLayout);
                chineseInputEngine.appendAsciiLetter(output);
            }
            commitPendingEngineText(ic);
            syncComposition(ic);
            return;
        }
        commitPendingComposition(ic);
        ic.commitText(output, 1);
        updateAutoShiftAfterText(output);
        keyboardView.consumeLetterShift();
    }

    private void handleReturn(InputConnection ic) {
        hidePunctuationPicker();
        if (commitFirstCandidate(ic)) {
            return;
        }
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action == EditorInfo.IME_ACTION_GO
                || action == EditorInfo.IME_ACTION_SEARCH
                || action == EditorInfo.IME_ACTION_SEND
                || action == EditorInfo.IME_ACTION_NEXT
                || action == EditorInfo.IME_ACTION_DONE) {
            ic.performEditorAction(action);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
        shouldAutoShift = true;
        keyboardView.setAutoShift(true);
    }

    private void handleSpace(InputConnection ic) {
        hidePunctuationPicker();
        if (commitFirstCandidate(ic)) {
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (shouldInsertDoubleSpacePeriod(before)) {
            deleteOneCodePoint(ic);
            ic.commitText(". ", 1);
            shouldAutoShift = true;
        } else {
            ic.commitText(" ", 1);
            shouldAutoShift = false;
        }
        keyboardView.setAutoShift(shouldAutoShift);
    }

    private void handleSeparator(InputConnection ic) {
        hidePunctuationPicker();
        if (!isChineseNineKeyComposing()) {
            return;
        }
        if (t9CompositionTokens.length() == 0
                || t9CompositionTokens.charAt(t9CompositionTokens.length() - 1) == '\'') {
            return;
        }
        t9CompositionTokens.append('\'');
        syncComposition(ic);
    }

    private void switchLanguage(InputConnection ic) {
        commitPendingComposition(ic);
        hidePunctuationPicker();
        hideCandidatePanel();
        language = language == InputLanguage.CHINESE ? InputLanguage.ENGLISH : InputLanguage.CHINESE;
        saveBaseInputMode();
        if (keyboardView != null) {
            setKeyboardMode(KeyboardMode.LETTERS);
            keyboardView.setLanguage(language);
            keyboardView.setChineseKeyboardLayout(chineseKeyboardLayout);
            keyboardView.setAutoShift(language == InputLanguage.ENGLISH && shouldAutoShift);
            updateReturnKeyAppearance(getCurrentInputEditorInfo());
        }
        updateCandidates();
    }

    private void cycleInputMode(InputConnection ic) {
        commitPendingComposition(ic);
        hidePunctuationPicker();
        hideCandidatePanel();
        if (language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            language = InputLanguage.ENGLISH;
            chineseKeyboardLayout = ChineseKeyboardLayout.QWERTY;
        } else if (language == InputLanguage.CHINESE) {
            chineseKeyboardLayout = ChineseKeyboardLayout.NINE_KEY;
        } else {
            language = InputLanguage.CHINESE;
            chineseKeyboardLayout = ChineseKeyboardLayout.NINE_KEY;
        }
        saveBaseInputMode();
        if (chineseInputEngine != null) {
            chineseInputEngine.setLayout(chineseKeyboardLayout);
        }
        if (keyboardView != null) {
            setKeyboardMode(KeyboardMode.LETTERS);
            keyboardView.setLanguage(language);
            keyboardView.setChineseKeyboardLayout(chineseKeyboardLayout);
            keyboardView.setAutoShift(language == InputLanguage.ENGLISH && shouldAutoShift);
            updateReturnKeyAppearance(getCurrentInputEditorInfo());
        }
        updateCandidates();
    }

    private void switchChineseKeyboardLayout(InputConnection ic) {
        commitPendingComposition(ic);
        hidePunctuationPicker();
        hideCandidatePanel();
        chineseKeyboardLayout = chineseKeyboardLayout == ChineseKeyboardLayout.QWERTY
                ? ChineseKeyboardLayout.NINE_KEY
                : ChineseKeyboardLayout.QWERTY;
        language = InputLanguage.CHINESE;
        saveBaseInputMode();
        if (chineseInputEngine != null) {
            chineseInputEngine.setLayout(chineseKeyboardLayout);
        }
        if (keyboardView != null) {
            setKeyboardMode(KeyboardMode.LETTERS);
            keyboardView.setLanguage(language);
            keyboardView.setChineseKeyboardLayout(chineseKeyboardLayout);
            keyboardView.setAutoShift(false);
            updateReturnKeyAppearance(getCurrentInputEditorInfo());
        }
        updateCandidates();
    }

    private boolean commitFirstCandidate(InputConnection ic) {
        if (language != InputLanguage.CHINESE
                || chineseInputEngine == null
                || (!chineseInputEngine.hasComposition() && !isChineseNineKeyComposing())) {
            return false;
        }
        if (isChineseNineKeyComposing() && isT9PinyinSelectionActive()) {
            String text = activeChineseCandidates.isEmpty()
                    ? selectedT9Pinyin
                    : activeChineseCandidates.get(0);
            commitSelectedT9SyllableCandidate(ic, text);
            return true;
        }
        if (isChineseNineKeyComposing()
                && t9CompositionTokens.length() == 0
                && t9SelectedText.length() > 0) {
            ic.commitText(t9SelectedText.toString(), 1);
            finishCommittedChineseText();
            return true;
        }
        if (isChineseNineKeyComposing()) {
            if (activeChineseCandidates.isEmpty()) {
                ic.finishComposingText();
                String raw = chineseInputEngine.commitBestCandidateOrRaw();
                if (raw.isEmpty()) {
                    updateCandidates();
                    return false;
                }
                ic.commitText(raw, 1);
                finishCommittedChineseText();
                return true;
            }
            commitCandidate(ic, activeChineseCandidates.get(0));
            return true;
        }

        ic.finishComposingText();
        List<String> candidates = currentCandidates();
        if (candidates.isEmpty()) {
            String raw = chineseInputEngine.commitBestCandidateOrRaw();
            if (raw.isEmpty()) {
                updateCandidates();
                return false;
            }
            ic.commitText(raw, 1);
            finishCommittedChineseText();
            return true;
        }
        commitCandidate(ic, candidates.get(0));
        return true;
    }

    private void commitPendingComposition(InputConnection ic) {
        if (isChineseNineKeyComposing()) {
            commitFirstCandidate(ic);
            return;
        }
        if (chineseInputEngine == null || !chineseInputEngine.hasComposition()) {
            return;
        }
        ic.finishComposingText();
        String raw = chineseInputEngine.commitRaw();
        if (!raw.isEmpty()) {
            ic.commitText(raw, 1);
        }
        updateCandidates();
    }

    private void commitPendingEngineText(InputConnection ic) {
        if (chineseInputEngine == null) {
            return;
        }
        String text = chineseInputEngine.consumePendingCommit();
        if (text != null && !text.isEmpty()) {
            ic.finishComposingText();
            ic.commitText(text, 1);
            clearT9State();
            finishCommittedChineseText();
        }
    }

    private void commitCandidate(InputConnection ic, String text) {
        boolean t9Composing = isChineseNineKeyComposing();
        String learningContext = language == InputLanguage.CHINESE
                ? currentChineseLearningContext()
                : "";
        if (!t9Composing) {
            ic.finishComposingText();
        }
        String committed = chineseInputEngine == null
                ? text
                : chineseInputEngine.commitCandidate(text);
        if (committed != null && !committed.isEmpty() && language == InputLanguage.CHINESE) {
            String learnedCandidate = t9Composing && t9SelectedText.length() > 0
                    ? t9SelectedText + text
                    : text;
            if (!learningContext.isEmpty()) {
                recordChineseCandidateSelection(learningContext, learnedCandidate);
            }
        }
        if (committed != null && !committed.isEmpty()) {
            if (t9Composing && t9SelectedText.length() > 0) {
                committed = t9SelectedText + committed;
            }
            ic.commitText(committed, 1);
        }
        finishCommittedChineseText();
    }

    private void commitSelectedT9SyllableCandidate(InputConnection ic, String text) {
        if (text == null || text.isEmpty() || !isT9PinyinSelectionActive()) {
            return;
        }
        if (learningAllowed && localPinyinEngine != null) {
            localPinyinEngine.recordSelection(selectedT9Pinyin, text);
        }
        appendT9SelectedPinyinHistory(selectedT9Pinyin);
        t9SelectedText.append(text);
        removeLeadingT9Tokens(selectedT9PinyinDigitLength);
        clearSelectedT9Pinyin();
        rebuildChineseEngineForRemainingT9();

        if (t9CompositionTokens.length() == 0) {
            ic.commitText(t9SelectedText.toString(), 1);
            finishCommittedChineseText();
            return;
        }
        syncComposition(ic);
    }

    private void finishCommittedChineseText() {
        shouldAutoShift = false;
        clearT9State();
        if (keyboardView != null) {
            keyboardView.setAutoShift(false);
        }
        updateCandidates();
    }

    private void syncComposition(InputConnection ic) {
        if (isChineseNineKeyLettersMode()) {
            if (t9CompositionTokens.length() == 0 && t9SelectedText.length() == 0) {
                activePinyinText = "";
                t9PinyinResult = T9PinyinDecoder.EMPTY;
                activeChineseCandidates = Collections.emptyList();
                activeChineseCandidateAnnotations = Collections.emptyList();
                ic.setComposingText("", 1);
                ic.finishComposingText();
            } else if (t9CompositionTokens.length() == 0) {
                activePinyinText = "";
                t9PinyinResult = T9PinyinDecoder.EMPTY;
                activeChineseCandidates = Collections.emptyList();
                activeChineseCandidateAnnotations = Collections.emptyList();
                ic.setComposingText(t9ComposingDisplayText(), 1);
            } else {
                recalculateT9State();
                ic.setComposingText(t9ComposingDisplayText(), 1);
            }
            updateCandidates();
            return;
        }
        if (chineseInputEngine == null || !chineseInputEngine.hasComposition()) {
            ic.finishComposingText();
        } else {
            ic.setComposingText(chineseInputEngine.preedit(), 1);
        }
        updateCandidates();
    }

    private List<String> currentCandidates() {
        if (isChineseNineKeyComposing()) {
            return activeChineseCandidates;
        }
        if (chineseInputEngine == null || !chineseInputEngine.hasComposition()) {
            return Collections.emptyList();
        }
        chineseInputEngine.setLayout(chineseKeyboardLayout);
        List<String> candidates = chineseInputEngine.candidates();
        if (!learningAllowed || localPinyinEngine == null) {
            return candidates;
        }
        String context = currentChineseLearningContext();
        if (context.isEmpty()) {
            return candidates;
        }
        return localPinyinEngine.prioritizeBySelectionFrequency(context, candidates);
    }

    private void setKeyboardMode(KeyboardMode mode) {
        hidePunctuationPicker();
        hideCandidatePanel();
        keyboardMode = mode;
        if (keyboardView != null) {
            keyboardView.setMode(mode);
        }
    }

    private void updateCandidates() {
        if (keyboardView != null) {
            keyboardView.setComposing(isChineseNineKeyComposing());
        }
        if (candidateStripView != null) {
            candidateStripView.setReserveSpaceWhenEmpty(shouldReserveCandidateStrip());
            if (punctuationPickerVisible) {
                candidateStripView.setPunctuationCandidates(PUNCTUATION_STRIP_CANDIDATES);
                updateExpandedCandidatePanel();
                return;
            }
            if (isChineseNineKeyComposing()) {
                candidateStripView.setCandidateRows(
                        orderedT9PinyinCandidates(),
                        activeChineseCandidates,
                        true);
                updateExpandedCandidatePanel();
                return;
            }
            String composing = language == InputLanguage.CHINESE
                    && chineseInputEngine != null
                    && chineseInputEngine.hasComposition()
                    ? chineseInputEngine.preedit()
                    : "";
            candidateStripView.setCandidates(currentCandidates(), composing);
            updateExpandedCandidatePanel();
        }
    }

    private List<String> expandedPanelCandidates() {
        if (punctuationPickerVisible) {
            return PUNCTUATION_PANEL_CANDIDATES;
        }
        if (isChineseNineKeyComposing()) {
            return activeChineseCandidates == null ? Collections.emptyList() : activeChineseCandidates;
        }
        return currentCandidates();
    }

    private void updateExpandedCandidatePanel() {
        if (expandedCandidatePanelView == null || candidateStripView == null) {
            return;
        }
        List<String> candidates = expandedPanelCandidates();
        if (!candidatePanelExpanded || candidates.size() <= 6) {
            hideCandidatePanel();
            return;
        }
        expandedCandidatePanelView.setCandidates(candidates,
                punctuationPickerVisible ? PUNCTUATION_STRIP_CANDIDATES.size() : 6);
        expandedCandidatePanelView.setVisibility(View.VISIBLE);
        if (keyboardView != null) {
            keyboardView.setVisibility(View.GONE);
        }
        candidateStripView.setExpanded(true);
        expandedCandidatePanelView.requestLayout();
    }

    private void hideCandidatePanel() {
        candidatePanelExpanded = false;
        if (expandedCandidatePanelView != null) {
            expandedCandidatePanelView.setVisibility(View.GONE);
            expandedCandidatePanelView.setCandidates(Collections.emptyList());
            expandedCandidatePanelView.requestLayout();
        }
        if (keyboardView != null) {
            keyboardView.setVisibility(View.VISIBLE);
        }
        if (candidateStripView != null) {
            candidateStripView.setExpanded(false);
        }
    }

    private int keyboardAreaHeight(int width) {
        float referenceHeight = 662f;
        if (keyboardMode == KeyboardMode.EMOJI) {
            referenceHeight = 980f;
        } else if (language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            referenceHeight = 660f;
        } else if (keyboardMode == KeyboardMode.NUMBERS
                || keyboardMode == KeyboardMode.SYMBOLS
                || keyboardMode == KeyboardMode.SYMBOLS_MORE) {
            referenceHeight = 662f;
        }
        return Math.max(dp(196), (int) (width * referenceHeight / IosKeyboardTheme.REFERENCE_WIDTH + 0.5f));
    }

    private boolean shouldReserveCandidateStrip() {
        return punctuationPickerVisible
                || (language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY)
                || (language == InputLanguage.CHINESE
                && chineseInputEngine != null
                && chineseInputEngine.hasComposition());
    }

    private void showPunctuationPicker(InputConnection ic) {
        if (chineseInputEngine != null && chineseInputEngine.hasComposition()) {
            commitFirstCandidate(ic);
        }
        hideCandidatePanel();
        punctuationPickerVisible = true;
        updateCandidates();
    }

    private void hidePunctuationPicker() {
        punctuationPickerVisible = false;
        resetPunctuationCycle();
    }

    private void commitPunctuationCandidate(InputConnection ic, String text) {
        punctuationPickerVisible = false;
        ic.finishComposingText();
        replaceActivePunctuationIfPossible(ic);
        ic.commitText(text, 1);
        resetPunctuationCycle();
        shouldAutoShift = false;
        if (keyboardView != null) {
            keyboardView.setAutoShift(false);
        }
        updateCandidates();
    }

    private void handlePunctuationCycle(InputConnection ic) {
        if (chineseInputEngine != null && chineseInputEngine.hasComposition()) {
            commitFirstCandidate(ic);
        }
        hideCandidatePanel();
        long now = SystemClock.uptimeMillis();
        boolean continueCycle = punctuationCycleActive
                && now - lastPunctuationTapMs <= PUNCTUATION_CYCLE_TIMEOUT_MS
                && replaceActivePunctuationIfPossible(ic);
        punctuationCycleIndex = continueCycle
                ? (punctuationCycleIndex + 1) % PUNCTUATION_CANDIDATES.size()
                : 0;
        String punctuation = PUNCTUATION_CANDIDATES.get(punctuationCycleIndex);
        ic.finishComposingText();
        ic.commitText(punctuation, 1);
        punctuationCycleActive = true;
        lastPunctuationTapMs = now;
        lastPunctuationText = punctuation;
        punctuationPickerVisible = true;
        shouldAutoShift = false;
        if (keyboardView != null) {
            keyboardView.setAutoShift(false);
        }
        updateCandidates();
    }

    private boolean replaceActivePunctuationIfPossible(InputConnection ic) {
        if (!punctuationCycleActive || lastPunctuationText.isEmpty()) {
            return false;
        }
        CharSequence before = ic.getTextBeforeCursor(lastPunctuationText.length(), 0);
        if (before == null || !lastPunctuationText.contentEquals(before)) {
            resetPunctuationCycle();
            return false;
        }
        ic.deleteSurroundingText(lastPunctuationText.length(), 0);
        return true;
    }

    private void resetPunctuationCycle() {
        punctuationCycleActive = false;
        punctuationCycleIndex = -1;
        lastPunctuationTapMs = 0L;
        lastPunctuationText = "";
    }

    private void restoreBaseInputMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        language = enumPreference(prefs, PREF_LANGUAGE, InputLanguage.CHINESE);
        chineseKeyboardLayout = enumPreference(prefs, PREF_CHINESE_LAYOUT, ChineseKeyboardLayout.NINE_KEY);
    }

    private void saveBaseInputMode() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_LANGUAGE, language.name())
                .putString(PREF_CHINESE_LAYOUT, chineseKeyboardLayout.name())
                .apply();
    }

    private <T extends Enum<T>> T enumPreference(SharedPreferences prefs, String key, T fallback) {
        String value = prefs.getString(key, fallback.name());
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return fallback;
        }
    }

    private void resetTransientKeyboardMode() {
        hidePunctuationPicker();
        hideCandidatePanel();
        keyboardMode = KeyboardMode.LETTERS;
        if (keyboardView != null) {
            keyboardView.setMode(KeyboardMode.LETTERS);
            keyboardView.setLanguage(language);
            keyboardView.setChineseKeyboardLayout(chineseKeyboardLayout);
            keyboardView.setAutoShift(language == InputLanguage.ENGLISH && shouldAutoShift);
        }
        if (chineseInputEngine != null) {
            chineseInputEngine.setLayout(chineseKeyboardLayout);
        }
    }

    private void clearComposition() {
        if (chineseInputEngine != null) {
            chineseInputEngine.reset();
        }
        hideCandidatePanel();
        clearT9State();
        if (keyboardView != null) {
            keyboardView.setComposing(false);
        }
    }

    private boolean isChineseNineKeyLettersMode() {
        return language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && keyboardMode == KeyboardMode.LETTERS;
    }

    private boolean isChineseNineKeyComposing() {
        return isChineseNineKeyLettersMode()
                && (t9CompositionTokens.length() > 0 || t9SelectedText.length() > 0);
    }

    private void recalculateT9State() {
        if (t9CompositionTokens.length() == 0) {
            activePinyinText = "";
            t9PinyinResult = T9PinyinDecoder.EMPTY;
            activeChineseCandidates = Collections.emptyList();
            activeChineseCandidateAnnotations = Collections.emptyList();
            return;
        }
        t9PinyinResult = t9PinyinDecoder.decode(t9CompositionTokens.toString());
        if (isT9PinyinSelectionActive()) {
            activePinyinText = displayTextForSelectedPinyin(selectedT9Pinyin, selectedT9PinyinDigitLength);
            activeChineseCandidates = candidatesForSelectedPinyin(selectedT9Pinyin);
            activeChineseCandidateAnnotations = repeatAnnotation(activeChineseCandidates.size(), selectedT9Pinyin);
        } else {
            List<String> originalEngineCandidates = chineseInputEngine == null
                    ? Collections.emptyList()
                    : chineseInputEngine.candidates();
            List<String> originalEngineAnnotations = chineseInputEngine == null
                    ? Collections.emptyList()
                    : chineseInputEngine.candidateAnnotations();
            List<String> engineCandidates = originalEngineCandidates;
            List<String> engineAnnotations = originalEngineAnnotations;
            if (learningAllowed && localPinyinEngine != null) {
                String context = currentT9LearningContext(t9PinyinResult.bestDisplayText);
                if (!context.isEmpty()) {
                    engineCandidates = localPinyinEngine.prioritizeBySelectionFrequency(context, engineCandidates);
                    engineAnnotations = alignCandidateAnnotations(
                            originalEngineCandidates,
                            originalEngineAnnotations,
                            engineCandidates);
                }
            }
            List<String> abbreviationCandidates = localPinyinEngine == null
                    ? Collections.emptyList()
                    : localPinyinEngine.abbreviationCandidatesForNineKey(
                            t9CompositionTokens.toString(),
                            learningAllowed);
            boolean promoteAbbreviation = shouldPromoteT9AbbreviationCandidates();
            activePinyinText = t9PinyinResult.bestDisplayText;
            activeChineseCandidates = mergeT9Candidates(
                    engineCandidates,
                    abbreviationCandidates,
                    promoteAbbreviation);
            activeChineseCandidateAnnotations = mergeT9CandidateAnnotations(
                    engineCandidates,
                    engineAnnotations,
                    abbreviationCandidates,
                    promoteAbbreviation);
            activePinyinText = preferredT9DisplayText(
                    t9PinyinResult.bestDisplayText,
                    activeChineseCandidateAnnotations);
        }
    }

    private List<String> mergeT9Candidates(
            List<String> engineCandidates,
            List<String> abbreviationCandidates,
            boolean promoteAbbreviation) {
        List<String> merged = new ArrayList<>();
        if (promoteAbbreviation) {
            appendT9Candidates(merged, abbreviationCandidates);
        }
        appendT9Candidates(merged, engineCandidates);
        if (!promoteAbbreviation) {
            appendT9Candidates(merged, abbreviationCandidates);
        }
        if (merged.isEmpty() && activePinyinText != null && !activePinyinText.isEmpty()) {
            merged.add(activePinyinText.replace(" ", ""));
        }
        return Collections.unmodifiableList(merged);
    }

    private List<String> mergeT9CandidateAnnotations(
            List<String> engineCandidates,
            List<String> annotations,
            List<String> abbreviationCandidates,
            boolean promoteAbbreviation) {
        List<String> mergedCandidates = new ArrayList<>();
        List<String> mergedAnnotations = new ArrayList<>();
        String digits = t9CompositionTokens.toString().replace("'", "");
        if (promoteAbbreviation) {
            appendT9AbbreviationAnnotations(
                    abbreviationCandidates,
                    mergedCandidates,
                    mergedAnnotations,
                    digits);
        }
        if (engineCandidates != null) {
            for (int i = 0; i < engineCandidates.size(); i++) {
                String candidate = engineCandidates.get(i);
                if (candidate == null || candidate.isEmpty() || candidate.matches("[0-9']+")) {
                    continue;
                }
                if (!mergedCandidates.contains(candidate)) {
                    mergedCandidates.add(candidate);
                    String annotation = annotations != null && i < annotations.size() ? annotations.get(i) : "";
                    annotation = normalizeCandidateAnnotation(annotation);
                    if (annotation.isEmpty() && localPinyinEngine != null) {
                        annotation = localPinyinEngine.pinyinForCandidateMatchingDigits(candidate, digits);
                        if (annotation.isEmpty()) {
                            annotation = localPinyinEngine.pinyinPrefixForCandidateMatchingDigits(candidate, digits);
                        }
                    }
                    mergedAnnotations.add(annotation);
                }
            }
        }
        if (!promoteAbbreviation) {
            appendT9AbbreviationAnnotations(
                    abbreviationCandidates,
                    mergedCandidates,
                    mergedAnnotations,
                    digits);
        }
        if (mergedCandidates.isEmpty() && activePinyinText != null && !activePinyinText.isEmpty()) {
            mergedAnnotations.add(activePinyinText);
        }
        return Collections.unmodifiableList(mergedAnnotations);
    }

    private void appendT9Candidates(List<String> merged, List<String> candidates) {
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || candidate.matches("[0-9']+")) {
                continue;
            }
            if (!merged.contains(candidate)) {
                merged.add(candidate);
            }
        }
    }

    private void appendT9AbbreviationAnnotations(
            List<String> candidates,
            List<String> mergedCandidates,
            List<String> mergedAnnotations,
            String digits) {
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || candidate.matches("[0-9']+")
                    || mergedCandidates.contains(candidate)) {
                continue;
            }
            mergedCandidates.add(candidate);
            String annotation = localPinyinEngine == null
                    ? ""
                    : localPinyinEngine.pinyinForCandidateMatchingAbbreviationDigits(candidate, digits);
            if (annotation.isEmpty()) {
                annotation = T9AbbreviationDecoder.preferredDisplayForDigits(digits);
            }
            mergedAnnotations.add(annotation);
        }
    }

    private boolean shouldPromoteT9AbbreviationCandidates() {
        String digits = t9CompositionTokens.toString().replace("'", "");
        if (digits.length() < 2) {
            return false;
        }
        char first = digits.charAt(0);
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private List<String> alignCandidateAnnotations(
            List<String> originalCandidates,
            List<String> originalAnnotations,
            List<String> reorderedCandidates) {
        if (reorderedCandidates == null || reorderedCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (originalCandidates == null || originalAnnotations == null
                || originalCandidates.isEmpty() || originalAnnotations.isEmpty()) {
            return Collections.emptyList();
        }
        boolean[] used = new boolean[originalCandidates.size()];
        List<String> aligned = new ArrayList<>(reorderedCandidates.size());
        for (String candidate : reorderedCandidates) {
            int index = indexOfUnusedCandidate(originalCandidates, used, candidate);
            aligned.add(index >= 0 && index < originalAnnotations.size() ? originalAnnotations.get(index) : "");
        }
        return aligned;
    }

    private int indexOfUnusedCandidate(List<String> candidates, boolean[] used, String target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (!used[i] && target.equals(candidates.get(i))) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    private List<String> repeatAnnotation(int size, String annotation) {
        if (size <= 0) {
            return Collections.emptyList();
        }
        List<String> annotations = new ArrayList<>(size);
        String normalized = normalizeCandidateAnnotation(annotation);
        for (int i = 0; i < size; i++) {
            annotations.add(normalized);
        }
        return Collections.unmodifiableList(annotations);
    }

    private String normalizeCandidateAnnotation(String annotation) {
        if (annotation == null) {
            return "";
        }
        return annotation
                .replace('`', ' ')
                .replace('\'', ' ')
                .trim();
    }

    private String preferredT9DisplayText(String fallback, List<String> annotations) {
        if (annotations != null) {
            for (String annotation : annotations) {
                String normalized = normalizeCandidateAnnotation(annotation);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        return fallback == null ? "" : fallback;
    }

    private List<String> orderedT9PinyinCandidates() {
        List<String> candidates = t9PinyinResult.pinyinCandidates;
        if (candidates.isEmpty()) {
            candidates = pinyinCandidatesFromAnnotations();
        }
        String firstSyllable = firstPinyinSyllable(activePinyinText);
        if (firstSyllable.isEmpty()
                || candidates.isEmpty()
                || candidates.get(0).equals(firstSyllable)
                || !candidates.contains(firstSyllable)) {
            return candidates;
        }
        List<String> ordered = new ArrayList<>(candidates.size());
        ordered.add(firstSyllable);
        for (String candidate : candidates) {
            if (!firstSyllable.equals(candidate)) {
                ordered.add(candidate);
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    private String firstPinyinSyllable(String text) {
        String normalized = normalizeCandidateAnnotation(text);
        if (normalized.isEmpty()) {
            return "";
        }
        int space = normalized.indexOf(' ');
        return space > 0 ? normalized.substring(0, space) : normalized;
    }

    private List<String> pinyinCandidatesFromAnnotations() {
        if (activeChineseCandidateAnnotations == null || activeChineseCandidateAnnotations.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> candidates = new ArrayList<>();
        for (String annotation : activeChineseCandidateAnnotations) {
            String first = firstPinyinSyllable(annotation);
            if (!first.isEmpty() && !candidates.contains(first)) {
                candidates.add(first);
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    private boolean removeLastT9Token() {
        if (t9CompositionTokens.length() == 0) {
            return false;
        }
        clearSelectedT9Pinyin();
        char removed = t9CompositionTokens.charAt(t9CompositionTokens.length() - 1);
        t9CompositionTokens.deleteCharAt(t9CompositionTokens.length() - 1);
        if (t9CompositionTokens.length() > 0
                && t9CompositionTokens.charAt(t9CompositionTokens.length() - 1) == '\'') {
            t9CompositionTokens.deleteCharAt(t9CompositionTokens.length() - 1);
        }
        return removed >= '2' && removed <= '9';
    }

    private void clearT9State() {
        t9CompositionTokens.setLength(0);
        t9SelectedText.setLength(0);
        t9SelectedPinyinHistory.setLength(0);
        t9PinyinResult = T9PinyinDecoder.EMPTY;
        activePinyinText = "";
        activeChineseCandidates = Collections.emptyList();
        activeChineseCandidateAnnotations = Collections.emptyList();
        clearSelectedT9Pinyin();
    }

    private void appendT9SelectedPinyinHistory(String pinyin) {
        String normalized = normalizePinyinForLearning(pinyin);
        if (normalized.isEmpty()) {
            return;
        }
        if (t9SelectedPinyinHistory.length() > 0) {
            t9SelectedPinyinHistory.append(' ');
        }
        t9SelectedPinyinHistory.append(normalized);
    }

    private void removeLastT9SelectedPinyinHistory() {
        if (t9SelectedPinyinHistory.length() == 0) {
            return;
        }
        int lastSpace = t9SelectedPinyinHistory.lastIndexOf(" ");
        if (lastSpace < 0) {
            t9SelectedPinyinHistory.setLength(0);
            return;
        }
        t9SelectedPinyinHistory.setLength(lastSpace);
    }

    private void recordChineseCandidateSelection(String context, String candidate) {
        if (!learningAllowed || localPinyinEngine == null
                || context == null || context.isEmpty()
                || candidate == null || candidate.isEmpty()) {
            return;
        }
        String normalizedContext = normalizePinyinForLearning(context);
        if (!normalizedContext.isEmpty()) {
            localPinyinEngine.recordSelection(normalizedContext, candidate);
        }
    }

    private String currentChineseLearningContext() {
        if (isChineseNineKeyComposing()) {
            return currentT9LearningContext();
        }
        if (chineseInputEngine != null && chineseInputEngine.hasComposition()) {
            return chineseInputEngine.preedit();
        }
        return "";
    }

    private String currentT9LearningContext() {
        return currentT9LearningContext(activePinyinText);
    }

    private String currentT9LearningContext(String activeText) {
        String selected = t9SelectedPinyinHistory.toString();
        String active = activeText == null ? "" : activeText;
        if (selected.isEmpty()) {
            return active;
        }
        if (active.isEmpty()) {
            return selected;
        }
        return selected + " " + active;
    }

    private String normalizePinyinForLearning(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(java.util.Locale.US);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || c == '\'' || c == ' ') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean isT9PinyinSelectionActive() {
        return selectedT9Pinyin != null
                && !selectedT9Pinyin.isEmpty()
                && selectedT9PinyinDigitLength > 0;
    }

    private void clearSelectedT9Pinyin() {
        selectedT9Pinyin = "";
        selectedT9PinyinDigitLength = 0;
    }

    private String t9ComposingDisplayText() {
        if (t9SelectedText.length() == 0) {
            return activePinyinText;
        }
        if (activePinyinText == null || activePinyinText.isEmpty()) {
            return t9SelectedText.toString();
        }
        return t9SelectedText + activePinyinText;
    }

    private String normalizeT9PinyinCandidate(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.trim().toLowerCase(java.util.Locale.US);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append(c);
            } else if (c == ' ' || c == '\'') {
                break;
            }
        }
        return out.toString();
    }

    private int leadingDigitLengthForPinyin(String pinyin) {
        String digits = T9PinyinDecoder.digitsForPinyin(pinyin);
        if (digits.isEmpty() || digits.length() > t9CompositionTokens.length()) {
            return 0;
        }
        String remaining = t9CompositionTokens.toString().replace("'", "");
        if (!remaining.startsWith(digits)) {
            return 0;
        }
        return digits.length();
    }

    private String displayTextForSelectedPinyin(String pinyin, int digitLength) {
        String remainder = remainingDigitsAfterLeadingLength(digitLength);
        T9PinyinDecoder.Result remainderResult = t9PinyinDecoder.decode(remainder);
        if (remainderResult.bestDisplayText.isEmpty()) {
            return pinyin;
        }
        return pinyin + " " + remainderResult.bestDisplayText;
    }

    private List<String> candidatesForSelectedPinyin(String pinyin) {
        if (localPinyinEngine == null) {
            return Collections.singletonList(pinyin);
        }
        List<String> candidates = localPinyinEngine.exactCandidatesWithRimeFallback(pinyin, learningAllowed);
        if (candidates == null || candidates.isEmpty()) {
            return Collections.singletonList(pinyin);
        }
        return candidates;
    }

    private String remainingDigitsAfterLeadingLength(int digitLength) {
        String digits = t9CompositionTokens.toString().replace("'", "");
        if (digitLength >= digits.length()) {
            return "";
        }
        return digits.substring(digitLength);
    }

    private void removeLeadingT9Tokens(int digitLength) {
        if (digitLength <= 0) {
            return;
        }
        int removedDigits = 0;
        int removeChars = 0;
        while (removeChars < t9CompositionTokens.length() && removedDigits < digitLength) {
            char c = t9CompositionTokens.charAt(removeChars);
            if (c >= '2' && c <= '9') {
                removedDigits++;
            }
            removeChars++;
        }
        while (removeChars < t9CompositionTokens.length() && t9CompositionTokens.charAt(removeChars) == '\'') {
            removeChars++;
        }
        t9CompositionTokens.delete(0, removeChars);
    }

    private void rebuildChineseEngineForRemainingT9() {
        if (chineseInputEngine == null) {
            return;
        }
        chineseInputEngine.reset();
        chineseInputEngine.setLayout(chineseKeyboardLayout);
        for (int i = 0; i < t9CompositionTokens.length(); i++) {
            char c = t9CompositionTokens.charAt(i);
            if (c >= '2' && c <= '9') {
                chineseInputEngine.appendNineKeyDigit(String.valueOf(c));
            }
        }
    }

    private void removeLastSelectedT9TextCodePoint() {
        if (t9SelectedText.length() == 0) {
            return;
        }
        int offset = t9SelectedText.offsetByCodePoints(t9SelectedText.length(), -1);
        t9SelectedText.delete(offset, t9SelectedText.length());
    }

    private ChineseInputEngine createChineseInputEngine() {
        ChineseInputEngine engine = RimeChineseInputEngine.create(this);
        return engine == null ? new LegacyPinyinInputEngine(this) : engine;
    }

    private void styleImeSystemBars() {
        android.app.Dialog dialog = getWindow();
        if (dialog == null) {
            return;
        }
        android.view.Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(IosKeyboardTheme.KEYBOARD_BG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(IosKeyboardTheme.KEYBOARD_BG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        window.getDecorView().setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);
    }

    private boolean isAsciiLetter(String output) {
        return output != null
                && output.length() == 1
                && output.charAt(0) >= 'A'
                && output.charAt(0) <= 'z'
                && Character.isLetter(output.charAt(0));
    }

    private boolean isNineKeyPinyinDigit(String output) {
        return output != null
                && output.length() == 1
                && output.charAt(0) >= '2'
                && output.charAt(0) <= '9';
    }

    private boolean shouldAllowLearning(EditorInfo attribute) {
        if (attribute == null) {
            return false;
        }
        if ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            return false;
        }
        return !isSensitiveInputType(attribute.inputType);
    }

    private boolean isSensitiveInputType(int inputType) {
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        int clazz = inputType & InputType.TYPE_MASK_CLASS;
        if (clazz == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_URI
                    || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                    || variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS;
        }
        if (clazz == InputType.TYPE_CLASS_PHONE) {
            return true;
        }
        return clazz == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private boolean shouldInsertDoubleSpacePeriod(CharSequence before) {
        if (before == null || before.length() < 2) {
            return false;
        }
        char previous = before.charAt(before.length() - 1);
        char beforePrevious = before.charAt(before.length() - 2);
        return previous == ' '
                && beforePrevious != ' '
                && beforePrevious != '\n'
                && beforePrevious != '.'
                && beforePrevious != '!'
                && beforePrevious != '?';
    }

    private void switchInputMethod() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (switchToNextInputMethod(false)) {
            return;
        }
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private void deleteOneCodePoint(InputConnection ic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(1, 0);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void updateAutoShiftAfterText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        char c = text.charAt(text.length() - 1);
        shouldAutoShift = c == '.' || c == '!' || c == '?';
        keyboardView.setAutoShift(shouldAutoShift);
    }

    private void refreshAutoShiftFromContext(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) {
            shouldAutoShift = true;
        } else {
            char c = before.charAt(before.length() - 1);
            shouldAutoShift = c == '.' || c == '!' || c == '?' || c == '\n';
        }
        keyboardView.setAutoShift(shouldAutoShift);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

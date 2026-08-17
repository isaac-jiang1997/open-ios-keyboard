package dev.openkeyboard.ioskeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;

public final class IosKeyboardView extends View {
    interface Listener {
        void onKey(KeyboardKey key);
        void onBackspace();
    }

    private static final long BACKSPACE_REPEAT_DELAY_MS = 430;
    private static final long BACKSPACE_REPEAT_INTERVAL_MS = 58;
    private static final float EMOJI_REF_WIDTH = 1320f;
    private static final float EMOJI_REF_HEIGHT = 980f;
    private static final float EMOJI_GRID_TOP = 184f;
    private static final float EMOJI_GRID_BOTTOM = 780f;
    private static final float EMOJI_ROW_STEP = 150f;
    private static final String[] EMOJI_CATEGORIES = {"◴", "☺", "🐶", "🍎", "⚽", "🚗", "♢", "♡", "⚑"};
    private static final int[] EMOJI_CATEGORY_ROW_ANCHORS = {0, 4, 13, 14, 15, 16, 17, 17, 17};

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specialKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actionKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint characterPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint functionPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actionPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint characterShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint functionShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capsLockMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path reusablePath = new android.graphics.Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repeatBackspace = new Runnable() {
        @Override
        public void run() {
            if (pressedKey != null && pressedKey.action == KeyAction.BACKSPACE && listener != null) {
                listener.onBackspace();
                handler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS);
            }
        }
    };

    private Listener listener;
    private KeyboardMode mode = KeyboardMode.LETTERS;
    private InputLanguage language = InputLanguage.ENGLISH;
    private ChineseKeyboardLayout chineseKeyboardLayout = ChineseKeyboardLayout.QWERTY;
    private boolean shifted = true;
    private boolean capsLocked = false;
    private boolean composing = false;
    private boolean sensitiveInput = false;
    private String returnKeyLabel = "return";
    private boolean returnKeyIsAction = false;
    private boolean punctuationStripVisible = false;
    private KeyboardKey pressedKey;
    private long lastShiftTapMs = 0L;
    private List<List<KeyboardKey>> rows;
    private KeyPopupController popupController;
    private KeyPopupView popupView;
    private boolean keyPopupEnabled = false;
    private int touchSlop;
    private float emojiScrollY = 0f;
    private float emojiLastTouchY = 0f;
    private float emojiDownX = 0f;
    private float emojiDownY = 0f;
    private boolean emojiTouchMoved = false;
    private int selectedEmojiCategory = 0;
    private int pressedEmojiCategory = -1;
    private final int[] keyboardWindowLocation = new int[2];
    private final int[] popupWindowLocation = new int[2];

    public IosKeyboardView(Context context) {
        super(context);
        init();
    }

    public IosKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setAutoShift(boolean enabled) {
        if (mode == KeyboardMode.LETTERS && language == InputLanguage.ENGLISH && !capsLocked) {
            shifted = enabled;
            rebuildRows();
            invalidate();
        }
    }

    void setMode(KeyboardMode mode) {
        if (this.mode != mode) {
            pressedKey = null;
            handler.removeCallbacks(repeatBackspace);
            if (mode == KeyboardMode.EMOJI) {
                emojiScrollY = 0f;
                selectedEmojiCategory = 0;
            }
        }
        this.mode = mode;
        rebuildRows();
        invalidate();
    }

    void setLanguage(InputLanguage language) {
        this.language = language;
        shifted = language == InputLanguage.ENGLISH && mode == KeyboardMode.LETTERS && shifted;
        capsLocked = false;
        rebuildRows();
        invalidate();
    }

    void setChineseKeyboardLayout(ChineseKeyboardLayout chineseKeyboardLayout) {
        this.chineseKeyboardLayout = chineseKeyboardLayout;
        rebuildRows();
        invalidate();
    }

    void setComposing(boolean composing) {
        if (this.composing == composing) {
            return;
        }
        this.composing = composing;
        rebuildRows();
        invalidate();
    }

    void setPunctuationStripVisible(boolean visible) {
        if (punctuationStripVisible == visible) {
            return;
        }
        punctuationStripVisible = visible;
        rebuildRows();
        invalidate();
    }

    void consumeLetterShift() {
        if (mode == KeyboardMode.LETTERS && shifted && !capsLocked) {
            shifted = false;
            rebuildRows();
            invalidate();
        }
    }

    void toggleShift() {
        long now = SystemClock.uptimeMillis();
        if (now - lastShiftTapMs < 420) {
            capsLocked = !capsLocked;
            shifted = capsLocked;
        } else if (capsLocked) {
            capsLocked = false;
            shifted = false;
        } else {
            shifted = !shifted;
        }
        lastShiftTapMs = now;
        rebuildRows();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = desiredKeyboardHeight(width);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutKeys(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mode == KeyboardMode.EMOJI) {
            drawEmojiPanel(canvas);
            return;
        }
        canvas.drawColor(IosKeyboardTheme.KEYBOARD_BG);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (isCoveredByPunctuationStrip(rowIndex)) {
                continue;
            }
            List<KeyboardKey> row = rows.get(rowIndex);
            for (KeyboardKey key : row) {
                drawKey(canvas, key);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mode == KeyboardMode.EMOJI) {
            return handleEmojiTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedKey = keyAt(event.getX(), event.getY());
                if (pressedKey != null) {
                    if (keyPopupEnabled) {
                        applyPopupCommand(popupController.onPress(pressedKey, mode));
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (pressedKey.action == KeyAction.BACKSPACE) {
                        if (listener != null) {
                            listener.onBackspace();
                        }
                        handler.postDelayed(repeatBackspace, BACKSPACE_REPEAT_DELAY_MS);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                KeyboardKey current = keyAt(event.getX(), event.getY());
                if (current != pressedKey) {
                    pressedKey = current;
                    if (keyPopupEnabled) {
                        if (current == null) {
                            applyPopupCommand(popupController.onMoveOut());
                        } else {
                            applyPopupCommand(popupController.onMoveTo(current, mode));
                        }
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                KeyboardKey released = pressedKey;
                pressedKey = null;
                handler.removeCallbacks(repeatBackspace);
                invalidate();
                if (keyPopupEnabled) {
                    applyPopupCommand(popupController.onRelease());
                }
                if (released != null && released.action != KeyAction.BACKSPACE && listener != null) {
                    listener.onKey(released);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = null;
                handler.removeCallbacks(repeatBackspace);
                invalidate();
                if (keyPopupEnabled) {
                    applyPopupCommand(popupController.onCancel());
                }
                return true;
            default:
                return true;
        }
    }

    private boolean handleEmojiTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                emojiDownX = event.getX();
                emojiDownY = event.getY();
                emojiLastTouchY = event.getY();
                emojiTouchMoved = false;
                pressedKey = emojiKeyAt(event.getX(), event.getY());
                pressedEmojiCategory = pressedKey == null
                        ? emojiCategoryAt(event.getX(), event.getY())
                        : -1;
                if (pressedKey != null || pressedEmojiCategory >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (pressedKey != null && pressedKey.action == KeyAction.BACKSPACE) {
                        if (listener != null) {
                            listener.onBackspace();
                        }
                        handler.postDelayed(repeatBackspace, BACKSPACE_REPEAT_DELAY_MS);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - emojiDownX;
                float dyFromDown = event.getY() - emojiDownY;
                float dy = event.getY() - emojiLastTouchY;
                if (!emojiTouchMoved
                        && (Math.abs(dx) > touchSlop || Math.abs(dyFromDown) > touchSlop)) {
                    emojiTouchMoved = true;
                    pressedKey = null;
                    pressedEmojiCategory = -1;
                    handler.removeCallbacks(repeatBackspace);
                }
                if (emojiTouchMoved) {
                    scrollEmojiBy(-dy);
                }
                emojiLastTouchY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                KeyboardKey released = emojiTouchMoved ? null : pressedKey;
                int releasedCategory = emojiTouchMoved ? -1 : pressedEmojiCategory;
                pressedKey = null;
                pressedEmojiCategory = -1;
                handler.removeCallbacks(repeatBackspace);
                invalidate();
                if (released != null && released.action != KeyAction.BACKSPACE && listener != null) {
                    listener.onKey(released);
                } else if (releasedCategory >= 0) {
                    selectEmojiCategory(releasedCategory);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = null;
                pressedEmojiCategory = -1;
                emojiTouchMoved = false;
                handler.removeCallbacks(repeatBackspace);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private KeyboardKey emojiKeyAt(float x, float y) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<KeyboardKey> bottom = rows.get(rows.size() - 1);
        for (KeyboardKey key : bottom) {
            if (key.hit(x, y)) {
                return key;
            }
        }
        float sy = getHeight() / EMOJI_REF_HEIGHT;
        float gridTop = EMOJI_GRID_TOP * sy;
        float gridBottom = EMOJI_GRID_BOTTOM * sy;
        if (y < gridTop || y > gridBottom) {
            return null;
        }
        float contentY = y + emojiScrollY;
        for (int rowIndex = rows.size() - 2; rowIndex >= 0; rowIndex--) {
            List<KeyboardKey> row = rows.get(rowIndex);
            for (KeyboardKey key : row) {
                if (key.hit(x, contentY)) {
                    return key;
                }
            }
        }
        return null;
    }

    private int emojiCategoryAt(float x, float y) {
        float sx = getWidth() / EMOJI_REF_WIDTH;
        float sy = getHeight() / EMOJI_REF_HEIGHT;
        float top = 820f * sy;
        float bottom = 950f * sy;
        if (y < top || y > bottom) {
            return -1;
        }
        for (int i = 0; i < EMOJI_CATEGORIES.length; i++) {
            float cx = (235 + i * 110) * sx;
            float hitRadius = 52f * sx;
            if (Math.abs(x - cx) <= hitRadius) {
                return i;
            }
        }
        return -1;
    }

    private void selectEmojiCategory(int category) {
        if (rows == null || rows.size() <= 1) {
            return;
        }
        int clampedCategory = Math.max(0, Math.min(category, EMOJI_CATEGORY_ROW_ANCHORS.length - 1));
        int rowIndex = Math.min(EMOJI_CATEGORY_ROW_ANCHORS[clampedCategory], rows.size() - 2);
        selectedEmojiCategory = clampedCategory;
        float sy = getHeight() / EMOJI_REF_HEIGHT;
        float target = rowIndex * EMOJI_ROW_STEP * sy;
        emojiScrollY = Math.max(0f, Math.min(maxEmojiScroll(), target));
        invalidate();
    }

    private void scrollEmojiBy(float dy) {
        float previous = emojiScrollY;
        emojiScrollY = Math.max(0f, Math.min(maxEmojiScroll(), emojiScrollY + dy));
        if (emojiScrollY != previous) {
            invalidate();
        }
    }

    private void clampEmojiScroll() {
        emojiScrollY = Math.max(0f, Math.min(maxEmojiScroll(), emojiScrollY));
    }

    private float maxEmojiScroll() {
        if (rows == null || rows.size() <= 1 || getHeight() <= 0) {
            return 0f;
        }
        float maxBottom = 0f;
        for (int rowIndex = 0; rowIndex < rows.size() - 1; rowIndex++) {
            for (KeyboardKey key : rows.get(rowIndex)) {
                maxBottom = Math.max(maxBottom, key.bounds.bottom);
            }
        }
        float sy = getHeight() / EMOJI_REF_HEIGHT;
        return Math.max(0f, maxBottom - EMOJI_GRID_BOTTOM * sy + 14f * sy);
    }

    private float emojiScaleY(int width) {
        int height = getHeight() > 0 ? getHeight() : desiredKeyboardHeight(width);
        return height / EMOJI_REF_HEIGHT;
    }

    private void init() {
        setWillNotDraw(false);
        keyPaint.setColor(IosKeyboardTheme.KEY_BG);
        specialKeyPaint.setColor(IosKeyboardTheme.SPECIAL_KEY_BG);
        actionKeyPaint.setColor(IosKeyboardTheme.ACTION_BLUE);
        characterPressedPaint.setColor(IosKeyboardTheme.CHARACTER_KEY_PRESSED_BG);
        functionPressedPaint.setColor(IosKeyboardTheme.FUNCTION_KEY_PRESSED_BG);
        actionPressedPaint.setColor(IosKeyboardTheme.ACTION_KEY_PRESSED_BG);
        characterShadowPaint.setColor(IosKeyboardTheme.CHARACTER_KEY_SHADOW);
        functionShadowPaint.setColor(IosKeyboardTheme.FUNCTION_KEY_SHADOW);
        iconPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        iconFillPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        iconFillPaint.setStyle(Paint.Style.FILL);

        capsLockMarkPaint.setColor(Color.BLACK);

        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextAlign(Paint.Align.CENTER);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        popupController = new KeyPopupController();
        rebuildRows();
    }

    private void rebuildRows() {
        rows = IosKeyboardLayout.rows(
                mode,
                shifted,
                capsLocked,
                language,
                chineseKeyboardLayout,
                composing,
                punctuationStripVisible);
        if (getWidth() > 0 && getHeight() > 0) {
            layoutKeys(getWidth(), getHeight());
        }
    }

    private void layoutKeys(int width, int height) {
        if (rows == null) {
            return;
        }
        if (layoutFromIosReference(width, height)) {
            return;
        }

        float outerPadding = dp(3);
        float horizontalGap = dp(5.5f);
        float verticalGap = dp(11);
        float bottomInset = dp(8);
        float topInset = dp(8);
        float rowHeight = (height - topInset - bottomInset - verticalGap * (rows.size() - 1)) / rows.size();
        float letterUnitWidth = (width - outerPadding * 2 - horizontalGap * 9) / 10f;

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<KeyboardKey> row = rows.get(rowIndex);
            float y = topInset + rowIndex * (rowHeight + verticalGap);

            if (mode == KeyboardMode.LETTERS
                    && !(language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY)
                    && rowIndex < 3) {
                layoutLetterRow(width, rowIndex, row, y, rowHeight, outerPadding,
                        horizontalGap, letterUnitWidth);
            } else {
                layoutWeightedRow(width, row, y, rowHeight, outerPadding, horizontalGap);
            }
        }
    }

    private boolean layoutFromIosReference(int width, int height) {
        if (language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && mode == KeyboardMode.LETTERS) {
            layoutChineseNineKey(width, 660f, false);
            if (punctuationStripVisible) {
                layoutPunctuationStrip(width);
            }
            ensureKeyboardBottomPadding(height);
            return true;
        }
        if (mode == KeyboardMode.NUMBERS
                && language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            layoutChineseNineKey(width, 660f, true);
            ensureKeyboardBottomPadding(height);
            return true;
        }
        if ((mode == KeyboardMode.SYMBOLS || mode == KeyboardMode.SYMBOLS_MORE)
                && language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            layoutChineseNineKeySymbols(width);
            ensureKeyboardBottomPadding(height);
            return true;
        }
        if (mode == KeyboardMode.LETTERS
                && chineseKeyboardLayout != ChineseKeyboardLayout.NINE_KEY) {
            layoutQwertyLetters(width);
            ensureKeyboardBottomPadding(height);
            return true;
        }
        if (mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS || mode == KeyboardMode.SYMBOLS_MORE) {
            layoutQwertySymbols(width);
            ensureKeyboardBottomPadding(height);
            return true;
        }
        if (mode == KeyboardMode.EMOJI) {
            layoutEmojiPanel(width);
            return true;
        }
        return false;
    }

    private void layoutEmojiPanel(int width) {
        float sx = width / EMOJI_REF_WIDTH;
        float sy = emojiScaleY(width);
        float left = 34f * sx;
        float right = width - 34f * sx;
        float cellWidth = (right - left) / 8f;
        float cellHeight = 118f * sy;
        float cellHorizontalInset = 10f * sx;
        float[] centersX = new float[8];
        for (int i = 0; i < centersX.length; i++) {
            centersX[i] = left + cellWidth * (i + 0.5f);
        }
        int emojiRows = Math.max(0, rows.size() - 1);
        for (int rowIndex = 0; rowIndex < emojiRows; rowIndex++) {
            List<KeyboardKey> row = rows.get(rowIndex);
            for (int i = 0; i < row.size(); i++) {
                float cx = centersX[Math.min(i, centersX.length - 1)];
                float cy = (EMOJI_GRID_TOP + 54f + rowIndex * EMOJI_ROW_STEP) * sy;
                row.get(i).bounds.set(
                        cx - cellWidth / 2f + cellHorizontalInset,
                        cy - cellHeight / 2f,
                        cx + cellWidth / 2f - cellHorizontalInset,
                        cy + cellHeight / 2f);
            }
        }
        if (!rows.isEmpty()) {
            List<KeyboardKey> bottom = rows.get(rows.size() - 1);
            if (bottom.size() > 0) {
                bottom.get(0).bounds.set(30 * sx, 835 * sy, 145 * sx, 940 * sy);
            }
            if (bottom.size() > 1) {
                bottom.get(1).bounds.set(1175 * sx, 835 * sy, 1300 * sx, 940 * sy);
            }
        }
        clampEmojiScroll();
    }

    private void layoutQwertyLetters(int width) {
        float ref = 662f;
        applyFrames(rows.get(0), width, ref, new float[][]{
                {12, 24, 113, 135}, {144, 24, 113, 135}, {276, 24, 111, 135}, {406, 24, 113, 135}, {538, 24, 113, 135},
                {670, 24, 111, 135}, {800, 24, 113, 135}, {932, 24, 113, 135}, {1064, 24, 113, 135}, {1196, 24, 111, 135}
        });
        applyFrames(rows.get(1), width, ref, new float[][]{
                {78, 192, 113, 135}, {210, 192, 113, 135}, {342, 192, 111, 135}, {472, 192, 113, 135}, {604, 192, 113, 135},
                {736, 192, 111, 135}, {866, 192, 113, 135}, {998, 192, 113, 135}, {1130, 192, 111, 135}
        });
        applyFrames(rows.get(2), width, ref, new float[][]{
                {12, 360, 153, 135}, {210, 360, 113, 135}, {342, 360, 111, 135}, {472, 360, 113, 135}, {604, 360, 113, 135},
                {736, 360, 111, 135}, {866, 360, 113, 135}, {998, 360, 113, 135}, {1156, 360, 151, 135}
        });
        applyFrames(rows.get(3), width, ref, new float[][]{
                {12, 528, 145, 135}, {176, 528, 147, 135}, {342, 528, 637, 135}, {998, 528, 309, 135}
        });
    }

    private void layoutQwertySymbols(int width) {
        float ref = 662f;
        applyFrames(rows.get(0), width, ref, new float[][]{
                {12, 24, 113, 135}, {144, 24, 113, 135}, {276, 24, 111, 135}, {406, 24, 113, 135}, {538, 24, 113, 135},
                {670, 24, 111, 135}, {800, 24, 113, 135}, {932, 24, 113, 135}, {1064, 24, 113, 135}, {1196, 24, 111, 135}
        });
        applyFrames(rows.get(1), width, ref, new float[][]{
                {12, 192, 113, 135}, {144, 192, 113, 135}, {276, 192, 111, 135}, {406, 192, 113, 135}, {538, 192, 113, 135},
                {670, 192, 111, 135}, {800, 192, 113, 135}, {932, 192, 113, 135}, {1064, 192, 113, 135}, {1196, 192, 111, 135}
        });
        layoutQwertySymbolThirdRow(width, ref);
        applyFrames(rows.get(3), width, ref, new float[][]{
                {12, 528, 145, 135}, {176, 528, 147, 135}, {342, 528, 637, 135}, {998, 528, 309, 135}
        });
    }

    private void layoutQwertySymbolThirdRow(int width, float referenceHeight) {
        List<KeyboardKey> row = rows.get(2);
        if (row.size() != 7) {
            applyFrames(row, width, referenceHeight, new float[][]{
                    {12, 360, 153, 135}, {188, 360, 135, 135}, {346, 360, 135, 135}, {504, 360, 135, 135},
                    {662, 360, 135, 135}, {820, 360, 135, 135}, {978, 360, 135, 135}, {1156, 360, 151, 135}
            });
            return;
        }

        float sx = width / 1320f;
        float sy = desiredKeyboardHeight(width) / referenceHeight;
        float y = 360f * sy;
        float h = 135f * sy;
        float x = 12f * sx;
        float gap = 22f * sx;
        float sideWidth = 153f * sx;
        float deleteWidth = 151f * sx;
        float right = 1307f * sx;
        float middleWidth = (right - x - sideWidth - deleteWidth - gap * 6f) / 5f;

        row.get(0).bounds.set(x, y, x + sideWidth, y + h);
        x += sideWidth + gap;
        for (int i = 1; i <= 5; i++) {
            row.get(i).bounds.set(x, y, x + middleWidth, y + h);
            x += middleWidth + gap;
        }
        row.get(6).bounds.set(x, y, x + deleteWidth, y + h);
    }

    private void layoutChineseNineKeySymbols(int width) {
        float ref = 688f;
        applyFrames(rows.get(0), width, ref, new float[][]{
                {12, 50, 113, 135}, {144, 50, 113, 135}, {276, 50, 111, 135}, {406, 50, 113, 135}, {538, 50, 113, 135},
                {670, 50, 111, 135}, {800, 50, 113, 135}, {932, 50, 113, 135}, {1064, 50, 113, 135}, {1196, 50, 111, 135}
        });
        applyFrames(rows.get(1), width, ref, new float[][]{
                {12, 218, 113, 135}, {144, 218, 113, 135}, {276, 218, 111, 135}, {406, 218, 113, 135}, {538, 218, 113, 135},
                {670, 218, 111, 135}, {800, 218, 113, 135}, {932, 218, 113, 135}, {1064, 218, 113, 135}, {1196, 218, 111, 135}
        });
        applyFrames(rows.get(2), width, ref, new float[][]{
                {12, 386, 153, 135}, {188, 386, 135, 135}, {346, 386, 135, 135}, {504, 386, 135, 135},
                {662, 386, 135, 135}, {820, 386, 135, 135}, {978, 386, 135, 135}, {1156, 386, 151, 135}
        });
        applyFrames(rows.get(3), width, ref, new float[][]{
                {12, 554, 309, 135}, {342, 554, 637, 135}, {998, 554, 309, 135}
        });
    }

    private void layoutChineseNineKey(int width, float ref, boolean hasFourBottomKeys) {
        applyFrames(rows.get(0), width, ref, new float[][]{
                {10, 4, 245, 147}, {274, 4, 245, 147}, {538, 4, 245, 147}, {802, 4, 245, 147}, {1066, 4, 245, 147}
        });
        applyFrames(rows.get(1), width, ref, new float[][]{
                {10, 172, 245, 147}, {274, 172, 245, 147}, {538, 172, 245, 147}, {802, 172, 245, 147}, {1066, 172, 245, 147}
        });
        applyFrames(rows.get(2), width, ref, new float[][]{
                {10, 340, 245, 147}, {274, 340, 245, 147}, {538, 340, 245, 147}, {802, 340, 245, 147}, {1066, 340, 245, 315}
        });
        if (hasFourBottomKeys) {
            applyFrames(rows.get(3), width, ref, new float[][]{
                    {10, 508, 245, 147}, {274, 508, 245, 147}, {538, 508, 245, 147}, {802, 508, 245, 147}
            });
        } else {
            applyFrames(rows.get(3), width, ref, new float[][]{
                    {10, 508, 245, 147}, {274, 508, 245, 147}, {538, 508, 509, 147}
            });
        }
    }

    private void layoutPunctuationStrip(int width) {
        if (rows.size() < 5) {
            return;
        }
        if (rows.size() > 2 && rows.get(2).size() > 4) {
            KeyboardKey sideAction = rows.get(2).get(4);
            sideAction.bounds.set(
                    sideAction.bounds.left,
                    sideAction.bounds.top,
                    sideAction.bounds.right,
                    sideAction.bounds.top + sideAction.bounds.width() * 147f / 245f);
        }
        List<KeyboardKey> strip = rows.get(4);
        float sx = width / 1320f;
        float sy = desiredKeyboardHeight(width) / 660f;
        float y = 508f * sy;
        float height = 147f * sy;
        float left = 10f * sx;
        float right = 1310f * sx;
        float gap = 10f * sx;
        float itemWidth = (right - left - gap * (strip.size() - 1)) / strip.size();
        for (int i = 0; i < strip.size(); i++) {
            float x = left + i * (itemWidth + gap);
            strip.get(i).bounds.set(x, y, x + itemWidth, y + height);
        }
    }

    private void applyFrames(List<KeyboardKey> row, int width, float referenceHeight, float[][] frames) {
        float sx = width / 1320f;
        float sy = desiredKeyboardHeight(width) / referenceHeight;
        int count = Math.min(row.size(), frames.length);
        for (int i = 0; i < count; i++) {
            float[] f = frames[i];
            row.get(i).bounds.set(f[0] * sx, f[1] * sy, (f[0] + f[2]) * sx, (f[1] + f[3]) * sy);
        }
    }

    private void ensureKeyboardBottomPadding(int height) {
        float maxBottom = 0f;
        for (List<KeyboardKey> row : rows) {
            for (KeyboardKey key : row) {
                maxBottom = Math.max(maxBottom, key.bounds.bottom);
            }
        }
        float allowedBottom = height - dp(4);
        float overflow = maxBottom - allowedBottom;
        if (overflow <= 0f) {
            return;
        }
        for (List<KeyboardKey> row : rows) {
            for (KeyboardKey key : row) {
                key.bounds.offset(0f, -overflow);
            }
        }
    }

    private void layoutLetterRow(
            int width,
            int rowIndex,
            List<KeyboardKey> row,
            float y,
            float rowHeight,
            float outerPadding,
            float horizontalGap,
            float letterUnitWidth
    ) {
        float x = outerPadding;
        if (rowIndex == 1) {
            float rowWidth = row.size() * letterUnitWidth + (row.size() - 1) * horizontalGap;
            x = (width - rowWidth) / 2f;
        } else if (rowIndex == 2) {
            float rowWidth = 0f;
            for (KeyboardKey key : row) {
                rowWidth += key.widthUnits * letterUnitWidth;
            }
            rowWidth += (row.size() - 1) * horizontalGap;
            x = (width - rowWidth) / 2f;
        }

        for (KeyboardKey key : row) {
            float keyWidth = key.widthUnits * letterUnitWidth;
            key.bounds.set(x, y, x + keyWidth, y + rowHeight);
            x += keyWidth + horizontalGap;
        }
    }

    private void layoutWeightedRow(
            int width,
            List<KeyboardKey> row,
            float y,
            float rowHeight,
            float outerPadding,
            float horizontalGap
    ) {
        float totalUnits = 0f;
        for (KeyboardKey key : row) {
            totalUnits += key.widthUnits;
        }
        float available = width - outerPadding * 2 - horizontalGap * (row.size() - 1);
        float unitWidth = available / totalUnits;
        float x = outerPadding;
        for (KeyboardKey key : row) {
            float keyWidth = unitWidth * key.widthUnits;
            key.bounds.set(x, y, x + keyWidth, y + rowHeight);
            x += keyWidth + horizontalGap;
        }
    }

    private Paint pressedFillFor(KeyboardKey key) {
        if (key.action == KeyAction.RETURN) {
            return actionPressedPaint;
        }
        if (isSpecial(key)) {
            return functionPressedPaint;
        }
        return characterPressedPaint;
    }

    void setKeyPopupView(KeyPopupView popupView) {
        this.popupView = popupView;
        if (!keyPopupEnabled && popupView != null) {
            popupView.hide();
        }
    }

    private void applyPopupCommand(KeyPopupController.Command cmd) {
        if (popupView == null || cmd == null) {
            return;
        }
        int keyboardTop = keyboardTopInPopupCoords();
        switch (cmd.kind) {
            case SHOW:
                popupView.show(cmd.label, cmd.anchor, keyboardTop);
                break;
            case UPDATE:
                popupView.update(cmd.label, cmd.anchor, keyboardTop);
                break;
            case HIDE:
                popupView.hide();
                break;
            case NOOP:
            default:
                break;
        }
    }

    private int keyboardTopInPopupCoords() {
        if (popupView == null) {
            return getTop();
        }
        getLocationInWindow(keyboardWindowLocation);
        popupView.getLocationInWindow(popupWindowLocation);
        return keyboardWindowLocation[1] - popupWindowLocation[1];
    }

    private void drawKeyShadow(Canvas canvas, KeyboardKey key, RectF r, float radius) {
        Paint shadowPaint = isSpecial(key) ? functionShadowPaint : characterShadowPaint;
        float scale = IosKeyboardTheme.scale(getWidth());
        // KEY_SHADOW_OFFSET_DP * scale is naturally ≈ 1pt on the iOS reference width
        // (≤ 1f when scale ≤ 1); Math.max(1f, ...) guarantees the sliver stays visible
        // on smaller widths without ever exceeding 1pt at the reference resolution.
        float offset = Math.max(1f, IosKeyboardTheme.KEY_SHADOW_OFFSET_DP * scale);
        canvas.drawRoundRect(r.left, r.top + offset, r.right, r.bottom + offset,
                radius, radius, shadowPaint);
    }

    private void drawKey(Canvas canvas, KeyboardKey key) {
        if (isPunctuationStripKey(key)) {
            drawPunctuationStripKey(canvas, key);
            return;
        }

        Paint fill = isSpecial(key) ? specialKeyPaint : keyPaint;
        if (key.action == KeyAction.RETURN && returnKeyIsAction) {
            fill = actionKeyPaint;
        } else if (key.action == KeyAction.RETURN) {
            fill = specialKeyPaint;
        }
        // Shift key becomes white (like character keys) when active
        if (key.action == KeyAction.SHIFT && (shifted || capsLocked)) {
            fill = keyPaint;
        }
        if (key == pressedKey) {
            fill = pressedFillFor(key);
        }
        RectF r = key.bounds;
        float scale = IosKeyboardTheme.scale(getWidth());
        float radius = Math.max(dp(5), 12f * scale);
        drawKeyShadow(canvas, key, r, radius);
        canvas.drawRoundRect(r, radius, radius, fill);

        boolean isActionReturn = key.action == KeyAction.RETURN && returnKeyIsAction;
        textPaint.setColor(isActionReturn ? Color.WHITE : Color.BLACK);
        textPaint.setTextSize(textSizeFor(key));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        if (key.action == KeyAction.BACKSPACE) {
            drawBackspaceIcon(canvas, r);
        } else if (key.action == KeyAction.SHIFT) {
            drawShiftIcon(canvas, r);
        } else if (key.action == KeyAction.MODE_EMOJI) {
            drawSmileIcon(canvas, r);
        } else if (isNineKeyLetterCluster(key)) {
            drawNineKeyLetterCluster(canvas, key, r);
        } else if (isNineKeyPunctuationCluster(key)) {
            drawNineKeyPunctuationCluster(canvas, key, r);
        } else if (isNineKeyCaretKey(key)) {
            drawNineKeyCaretKey(canvas, r);
        } else {
            drawLabel(canvas, displayLabel(key), r, textPaint);
        }

        if (key.action == KeyAction.SHIFT && capsLocked) {
            drawCapsLockMark(canvas, r);
        }
    }

    private void drawPunctuationStripKey(Canvas canvas, KeyboardKey key) {
        float scale = IosKeyboardTheme.scale(getWidth());
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextSize(Math.max(dp(18), 42f * scale));
        drawLabel(canvas, key.label, key.bounds, textPaint);
    }

    private void drawBackspaceIcon(Canvas canvas, RectF r) {
        float scale = IosKeyboardTheme.scale(getWidth());
        float cx = r.centerX();
        float cy = r.centerY();
        float w = 52f * scale;
        float h = 38f * scale;
        // Filled shape (iOS style: solid black body)
        reusablePath.reset();
        reusablePath.moveTo(cx - w * 0.18f, cy - h * 0.5f);
        reusablePath.lineTo(cx + w * 0.5f, cy - h * 0.5f);
        reusablePath.quadTo(cx + w * 0.62f, cy - h * 0.5f, cx + w * 0.62f, cy - h * 0.38f);
        reusablePath.lineTo(cx + w * 0.62f, cy + h * 0.38f);
        reusablePath.quadTo(cx + w * 0.62f, cy + h * 0.5f, cx + w * 0.5f, cy + h * 0.5f);
        reusablePath.lineTo(cx - w * 0.18f, cy + h * 0.5f);
        reusablePath.lineTo(cx - w * 0.62f, cy);
        reusablePath.close();
        iconFillPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        canvas.drawPath(reusablePath, iconFillPaint);
        // White X inside the filled shape
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStrokeWidth(Math.max(dp(1.5f), 3.8f * scale));
        canvas.drawLine(cx + w * 0.06f, cy - h * 0.18f, cx + w * 0.34f, cy + h * 0.18f, iconPaint);
        canvas.drawLine(cx + w * 0.34f, cy - h * 0.18f, cx + w * 0.06f, cy + h * 0.18f, iconPaint);
        iconPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
    }

    private void drawShiftIcon(Canvas canvas, RectF r) {
        float scale = IosKeyboardTheme.scale(getWidth());
        iconPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        iconPaint.setStrokeWidth(Math.max(dp(1.4f), 3.4f * scale));
        float cx = r.centerX();
        float cy = r.centerY() + 2f * scale;
        float w = 48f * scale;
        float h = 44f * scale;
        reusablePath.reset();
        reusablePath.moveTo(cx, cy - h * 0.58f);
        reusablePath.lineTo(cx + w * 0.46f, cy - h * 0.08f);
        reusablePath.lineTo(cx + w * 0.22f, cy - h * 0.08f);
        reusablePath.lineTo(cx + w * 0.22f, cy + h * 0.5f);
        reusablePath.lineTo(cx - w * 0.22f, cy + h * 0.5f);
        reusablePath.lineTo(cx - w * 0.22f, cy - h * 0.08f);
        reusablePath.lineTo(cx - w * 0.46f, cy - h * 0.08f);
        reusablePath.close();
        // iOS: hollow (stroke) when OFF, filled when ON or Caps Lock
        if (shifted || capsLocked) {
            iconFillPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
            canvas.drawPath(reusablePath, iconFillPaint);
        } else {
            canvas.drawPath(reusablePath, iconPaint);
        }
    }

    private void drawSmileIcon(Canvas canvas, RectF r) {
        float scale = IosKeyboardTheme.scale(getWidth());
        iconPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        iconPaint.setStrokeWidth(Math.max(dp(1.4f), 4f * scale));
        float cx = r.centerX();
        float cy = r.centerY();
        float radius = 35f * scale;
        canvas.drawCircle(cx, cy, radius, iconPaint);
        iconFillPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        canvas.drawCircle(cx - 13f * scale, cy - 9f * scale, 4f * scale, iconFillPaint);
        canvas.drawCircle(cx + 13f * scale, cy - 9f * scale, 4f * scale, iconFillPaint);
        canvas.drawArc(cx - 17f * scale, cy - 6f * scale, cx + 17f * scale,
                cy + 24f * scale, 10, 160, false, iconPaint);
    }

    private void drawNineKeyPunctuationCluster(Canvas canvas, KeyboardKey key, RectF r) {
        float scale = IosKeyboardTheme.scale(getWidth());
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextAlign(Paint.Align.CENTER);

        if ("，。?!".equals(key.label)) {
            drawCompactPunctuationText(canvas, r, scale);
            return;
        }

        textPaint.setTextSize(Math.max(dp(15), 34f * scale));
        drawLabel(canvas, key.label, r, textPaint);
    }

    private void drawNineKeyLetterCluster(Canvas canvas, KeyboardKey key, RectF r) {
        float scale = IosKeyboardTheme.scale(getWidth());
        String letters = key.label.replace(" ", "");
        int count = letters.length();
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.max(dp(18), (count == 4 ? 40f : 42f) * scale));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = r.centerY() - (fm.ascent + fm.descent) / 2f;
        float gap = Math.max(dp(2), (count == 4 ? 8f : 9f) * scale);
        float totalWidth = gap * (count - 1);
        for (int i = 0; i < count; i++) {
            totalWidth += textPaint.measureText(String.valueOf(letters.charAt(i)));
        }
        float x = r.centerX() - totalWidth / 2f;
        for (int i = 0; i < count; i++) {
            String letter = String.valueOf(letters.charAt(i));
            float letterWidth = textPaint.measureText(letter);
            canvas.drawText(letter, x + letterWidth / 2f, baseline, textPaint);
            x += letterWidth + gap;
        }
    }

    private void drawCompactPunctuationText(Canvas canvas, RectF r, float scale) {
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);

        float cx = r.centerX();
        float cy = r.centerY();
        float markSize = Math.max(dp(17), 39f * scale);
        textPaint.setTextSize(markSize);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        drawPunctuationGlyphOnBaseline(canvas, ",", cx - 39f * scale,
                baseline - 7f * scale, Math.max(dp(19), 43f * scale));
        drawManualIdeographicPeriod(canvas, cx - 17f * scale, cy + 9f * scale, scale);
        canvas.drawText("?", cx + 19f * scale, baseline, textPaint);
        canvas.drawText("!", cx + 43f * scale, baseline, textPaint);
    }

    private void drawPunctuationGlyphOnBaseline(
            Canvas canvas,
            String glyph,
            float cx,
            float baseline,
            float textSize) {
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextSize(textSize);
        canvas.drawText(glyph, cx, baseline, textPaint);
    }

    private void drawManualIdeographicPeriod(Canvas canvas, float cx, float cy, float scale) {
        Paint paint = iconPaint;
        paint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1f), 2.35f * scale));
        float radius = Math.max(dp(2.4f), 5.1f * scale);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
    }

    private void drawNineKeyCaretKey(Canvas canvas, RectF r) {
        float scale = IosKeyboardTheme.scale(getWidth());
        float cx = r.centerX();
        float cy = r.centerY();
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.max(dp(18), 42f * scale));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText("^^", cx, cy - (fm.ascent + fm.descent) / 2f - 8f * scale, textPaint);
        Paint underline = new Paint(Paint.ANTI_ALIAS_FLAG);
        underline.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        underline.setStrokeCap(Paint.Cap.ROUND);
        underline.setStrokeWidth(Math.max(dp(1.5f), 4f * scale));
        canvas.drawLine(cx - 14f * scale, cy + 22f * scale, cx + 14f * scale, cy + 22f * scale, underline);
    }

    private void drawEmojiPanel(Canvas canvas) {
        canvas.drawColor(Color.rgb(239, 241, 246));
        float sx = getWidth() / EMOJI_REF_WIDTH;
        float sy = getHeight() / EMOJI_REF_HEIGHT;
        float gridTop = EMOJI_GRID_TOP * sy;
        float gridBottom = EMOJI_GRID_BOTTOM * sy;

        Paint searchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        searchPaint.setColor(Color.rgb(249, 250, 253));
        canvas.drawRoundRect(24 * sx, 44 * sy, 1294 * sx, 150 * sy, 53 * sy, 53 * sy, searchPaint);
        drawEmojiSearchIcon(canvas, 78 * sx, 97 * sy, 25 * sx);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.rgb(132, 136, 145));
        textPaint.setTextSize(Math.max(dp(19), 41f * sx));
        Paint.FontMetrics searchFm = textPaint.getFontMetrics();
        canvas.drawText("搜索表情符号", 125 * sx,
                97 * sy - (searchFm.ascent + searchFm.descent) / 2f, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);

        canvas.save();
        canvas.clipRect(0f, gridTop, getWidth(), gridBottom);
        canvas.translate(0f, -emojiScrollY);
        int emojiRows = Math.max(0, rows.size() - 1);
        for (int rowIndex = 0; rowIndex < emojiRows; rowIndex++) {
            for (KeyboardKey key : rows.get(rowIndex)) {
                float drawTop = key.bounds.top - emojiScrollY;
                float drawBottom = key.bounds.bottom - emojiScrollY;
                if (drawBottom < gridTop || drawTop > gridBottom) {
                    continue;
                }
                if (key == pressedKey && !emojiTouchMoved) {
                    Paint pressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    pressPaint.setColor(Color.rgb(224, 228, 235));
                    canvas.drawOval(key.bounds, pressPaint);
                }
                textPaint.setColor(Color.BLACK);
                textPaint.setTextSize(Math.max(dp(24), 56f * sx));
                drawLabel(canvas, key.label, key.bounds, textPaint);
            }
        }
        canvas.restore();

        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(Color.rgb(204, 209, 217));
        dividerPaint.setStrokeWidth(Math.max(1f, 1.2f * sy));
        canvas.drawLine(0f, 812 * sy, getWidth(), 812 * sy, dividerPaint);

        if (!rows.isEmpty()) {
            List<KeyboardKey> bottom = rows.get(rows.size() - 1);
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(Math.max(dp(17), 38f * sx));
            if (bottom.size() > 0) {
                drawLabel(canvas, "ABC", bottom.get(0).bounds, textPaint);
            }
            textPaint.setTextSize(Math.max(dp(22), 52f * sx));
            if (bottom.size() > 1) {
                drawLabel(canvas, "⌫", bottom.get(1).bounds, textPaint);
            }
        }

        textPaint.setColor(IosKeyboardTheme.TEXT_SECONDARY);
        textPaint.setTextSize(Math.max(dp(20), 42f * sx));
        Paint selectedCategoryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedCategoryPaint.setColor(Color.rgb(220, 224, 231));
        canvas.drawCircle((235 + selectedEmojiCategory * 110) * sx, 888 * sy, 43 * sx, selectedCategoryPaint);
        for (int i = 0; i < EMOJI_CATEGORIES.length; i++) {
            canvas.drawText(EMOJI_CATEGORIES[i], (235 + i * 110) * sx, 902 * sy, textPaint);
        }
    }

    private void drawEmojiSearchIcon(Canvas canvas, float cx, float cy, float radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(132, 136, 145));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(dp(1.6f), radius * 0.12f));
        canvas.drawCircle(cx, cy, radius * 0.72f, paint);
        canvas.drawLine(cx + radius * 0.52f, cy + radius * 0.52f,
                cx + radius * 1.03f, cy + radius * 1.03f, paint);
    }

    private void drawCapsLockMark(Canvas canvas, RectF r) {
        capsLockMarkPaint.setStrokeWidth(dp(2));
        float y = r.bottom - dp(10);
        canvas.drawLine(r.centerX() - dp(7), y, r.centerX() + dp(7), y, capsLockMarkPaint);
    }

    void setReturnKeyAppearance(String label, boolean isAction) {
        this.returnKeyLabel = label == null ? "return" : label;
        this.returnKeyIsAction = isAction;
        invalidate();
    }

    void setSensitiveInput(boolean sensitive) {
        this.sensitiveInput = sensitive;
    }

    boolean isSensitiveInput() {
        return sensitiveInput;
    }

    private String displayLabel(KeyboardKey key) {
        if (key.action == KeyAction.RETURN) {
            return returnKeyLabel;
        }
        if (key.action == KeyAction.SHIFT) {
            return "⇧";
        }
        if (key.action == KeyAction.BACKSPACE) {
            return "⌫";
        }
        if (key.action == KeyAction.SWITCH_INPUT_METHOD) {
            return "◎";
        }
        return key.label;
    }

    private void drawLabel(Canvas canvas, String label, RectF r, TextPaint paint) {
        String[] lines = label.split("\\n", -1);
        float originalSize = paint.getTextSize();
        Paint.Align originalAlign = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.CENTER);
        fitTextSize(lines, r, paint);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) * (lines.length > 1 ? 0.92f : 1f);
        float firstBaseline = r.centerY() - (lineHeight * (lines.length - 1)) / 2f
                - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], r.centerX(), firstBaseline + i * lineHeight, paint);
        }
        paint.setTextSize(originalSize);
        paint.setTextAlign(originalAlign);
    }

    private void fitTextSize(String[] lines, RectF r, TextPaint paint) {
        float maxWidth = Math.max(dp(18), r.width() - Math.max(dp(10), 28f * IosKeyboardTheme.scale(getWidth())));
        float minSize = dp(13);
        while (paint.getTextSize() > minSize) {
            boolean fits = true;
            for (String line : lines) {
                if (paint.measureText(line) > maxWidth) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                return;
            }
            paint.setTextSize(paint.getTextSize() * 0.92f);
        }
    }

    private void drawCenteredText(Canvas canvas, String text, float cx, float cy, float textSize, TextPaint paint) {
        float oldSize = paint.getTextSize();
        Paint.Align oldAlign = paint.getTextAlign();
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint);
        paint.setTextSize(oldSize);
        paint.setTextAlign(oldAlign);
    }

    private float textSizeFor(KeyboardKey key) {
        float scale = IosKeyboardTheme.scale(getWidth());
        if (language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            if (key.action == KeyAction.RETURN || key.action == KeyAction.CONFIRM_COMPOSITION) {
                return Math.max(dp(18), 44f * scale);
            }
            if (key.action == KeyAction.SWITCH_CHINESE_LAYOUT) {
                return Math.max(dp(16), 38f * scale);
            }
            if (key.action == KeyAction.SPACE
                    || key.action == KeyAction.SELECT_WORDS
                    || key.action == KeyAction.SEPARATOR) {
                return Math.max(dp(17), 43f * scale);
            }
            if (key.action != KeyAction.CHARACTER) {
                return Math.max(dp(17), 42f * scale);
            }
            return Math.max(dp(18), 54f * scale);
        }
        if (key.action == KeyAction.RETURN) {
            return Math.max(dp(18), 46f * scale);
        }
        if (key.label != null && key.label.contains("\n")) {
            return Math.max(dp(16), 32f * scale);
        }
        if (key.action == KeyAction.CHARACTER) {
            return Math.max(dp(19), 64f * scale);
        }
        if (key.action == KeyAction.SPACE) {
            return Math.max(dp(16), 42f * scale);
        }
        return Math.max(dp(16), 42f * scale);
    }

    private boolean isSpecial(KeyboardKey key) {
        if (key.action == KeyAction.PUNCTUATION_PICKER
                || key.action == KeyAction.SEPARATOR
                || key.action == KeyAction.SELECT_WORDS
                || (language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && key.action == KeyAction.SWITCH_CHINESE_LAYOUT)) {
            return false;
        }
        return key.action != KeyAction.CHARACTER && key.action != KeyAction.SPACE;
    }

    private boolean isNineKeyPunctuationCluster(KeyboardKey key) {
        return language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && (key.action == KeyAction.CHARACTER || key.action == KeyAction.PUNCTUATION_PICKER)
                && ("，。?!".equals(key.label) || ".,;".equals(key.label));
    }

    private boolean isNineKeyLetterCluster(KeyboardKey key) {
        if (language != InputLanguage.CHINESE
                || chineseKeyboardLayout != ChineseKeyboardLayout.NINE_KEY
                || key.action != KeyAction.CHARACTER
                || key.label == null) {
            return false;
        }
        String letters = key.label.replace(" ", "");
        return letters.length() >= 3 && letters.length() <= 4 && letters.matches("[A-Z]+");
    }

    private boolean isNineKeyCaretKey(KeyboardKey key) {
        return language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && "^^".equals(key.label);
    }

    private boolean isPunctuationStripKey(KeyboardKey key) {
        return punctuationStripVisible
                && language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && mode == KeyboardMode.LETTERS
                && key.action == KeyAction.CHARACTER
                && key.label != null
                && key.label.length() <= 2
                && key.bounds.top > 0f;
    }

    private boolean isCoveredByPunctuationStrip(int rowIndex) {
        return punctuationStripVisible
                && language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY
                && mode == KeyboardMode.LETTERS
                && rows != null
                && rows.size() > 4
                && rowIndex == 3;
    }

    private KeyboardKey keyAt(float x, float y) {
        if (punctuationStripVisible && rows.size() > 4) {
            List<KeyboardKey> strip = rows.get(4);
            if (!strip.isEmpty() && y >= strip.get(0).bounds.top && y <= strip.get(0).bounds.bottom) {
                for (KeyboardKey key : strip) {
                    if (key.hit(x, y)) {
                        return key;
                    }
                }
                return null;
            }
        }
        for (int rowIndex = rows.size() - 1; rowIndex >= 0; rowIndex--) {
            if (isCoveredByPunctuationStrip(rowIndex)) {
                continue;
            }
            List<KeyboardKey> row = rows.get(rowIndex);
            for (KeyboardKey key : row) {
                if (key.hit(x, y)) {
                    return key;
                }
            }
        }
        return null;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int desiredKeyboardHeight(int width) {
        float referenceHeight;
        if (mode == KeyboardMode.LETTERS
                && language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            referenceHeight = 660f;
        } else if ((mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS || mode == KeyboardMode.SYMBOLS_MORE)
                && language == InputLanguage.CHINESE
                && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            referenceHeight = 660f;
        } else if (mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS || mode == KeyboardMode.SYMBOLS_MORE) {
            referenceHeight = 662f;
        } else if (mode == KeyboardMode.EMOJI) {
            referenceHeight = 980f;
        } else {
            referenceHeight = 662f;
        }
        return Math.max(dp(196), (int) (width * referenceHeight / 1320f + 0.5f));
    }
}

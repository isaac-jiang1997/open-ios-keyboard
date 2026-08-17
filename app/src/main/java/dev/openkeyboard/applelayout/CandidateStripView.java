package dev.openkeyboard.applelayout;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

public final class CandidateStripView extends LinearLayout {
    private static final char CANDIDATE_SEPARATOR = '\u001f';

    interface Listener {
        void onCandidate(String text);

        void onPinyinCandidate(String text);

        void onExpandCandidates();
    }

    private final HorizontalScrollView pinyinScroll;
    private final LinearLayout pinyinRow;
    private final View rowDivider;
    private final HorizontalScrollView candidateScroll;
    private final LinearLayout candidateRow;
    private final TextView expandButton;
    private final View divider;
    private Listener listener;
    private boolean reserveSpaceWhenEmpty;
    private boolean expanded;

    public CandidateStripView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);

        pinyinScroll = new HorizontalScrollView(context);
        pinyinScroll.setHorizontalScrollBarEnabled(false);
        pinyinScroll.setFillViewport(false);
        pinyinScroll.setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);

        pinyinRow = new LinearLayout(context);
        pinyinRow.setOrientation(HORIZONTAL);
        pinyinRow.setGravity(Gravity.CENTER_VERTICAL);
        pinyinScroll.addView(pinyinRow, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));
        addView(pinyinScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24)));

        rowDivider = new View(context);
        rowDivider.setBackgroundColor(Color.rgb(188, 193, 201));
        addView(rowDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);

        candidateScroll = new HorizontalScrollView(context);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidateScroll.setFillViewport(false);
        candidateScroll.setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);

        candidateRow = new LinearLayout(context);
        candidateRow.setOrientation(HORIZONTAL);
        candidateRow.setGravity(Gravity.CENTER_VERTICAL);
        candidateRow.setPadding(dp(11), 0, dp(5), 0);
        candidateScroll.addView(candidateRow, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));

        bottom.addView(candidateScroll, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));

        divider = new View(context);
        divider.setBackgroundColor(Color.rgb(168, 173, 181));
        bottom.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(28)));

        expandButton = new TextView(context);
        expandButton.setText("⌄");
        expandButton.setGravity(Gravity.CENTER);
        expandButton.setTextColor(IosKeyboardTheme.TEXT_SECONDARY);
        expandButton.setTextSize(24);
        expandButton.setIncludeFontPadding(false);
        expandButton.setOnClickListener(v -> {
            if (listener != null) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onExpandCandidates();
            }
        });
        bottom.addView(expandButton, new LinearLayout.LayoutParams(
                dp(38),
                LinearLayout.LayoutParams.MATCH_PARENT));

        addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(39)));

        setCandidateRows(Collections.emptyList(), Collections.emptyList(), false);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setReserveSpaceWhenEmpty(boolean reserveSpaceWhenEmpty) {
        this.reserveSpaceWhenEmpty = reserveSpaceWhenEmpty;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
        expandButton.setText(expanded ? "⌃" : "⌄");
    }

    void setCandidates(List<String> candidates, String composing) {
        if (composing != null && !composing.isEmpty()) {
            setCandidateRows(Collections.singletonList(composing), candidates, true);
        } else {
            setCandidateRows(Collections.emptyList(), candidates, false);
        }
    }

    void setCandidateRows(List<String> pinyinCandidates, List<String> chineseCandidates, boolean composing) {
        pinyinRow.removeAllViews();
        candidateRow.removeAllViews();

        boolean hasPinyin = pinyinCandidates != null && !pinyinCandidates.isEmpty();
        boolean hasChinese = chineseCandidates != null && !chineseCandidates.isEmpty();
        if (!hasPinyin && !hasChinese) {
            setVisibility(reserveSpaceWhenEmpty ? View.VISIBLE : View.GONE);
            pinyinScroll.setVisibility(View.GONE);
            rowDivider.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
            expandButton.setVisibility(View.GONE);
            setExpanded(false);
            pinyinScroll.scrollTo(0, 0);
            candidateScroll.scrollTo(0, 0);
            return;
        }

        setVisibility(View.VISIBLE);
        pinyinScroll.setVisibility(composing && hasPinyin ? View.VISIBLE : View.GONE);
        rowDivider.setVisibility(composing && hasPinyin && hasChinese ? View.VISIBLE : View.GONE);
        divider.setVisibility(composing && hasChinese ? View.VISIBLE : View.GONE);
        expandButton.setVisibility(composing && hasChinese ? View.VISIBLE : View.GONE);

        if (hasPinyin) {
            for (String pinyin : pinyinCandidates) {
                addPinyinText(pinyin);
            }
        }
        if (hasChinese) {
            for (String candidate : chineseCandidates) {
                addCandidateText(candidate, composing);
            }
        }
        pinyinScroll.scrollTo(0, 0);
        candidateScroll.scrollTo(0, 0);
    }

    void setPunctuationCandidates(List<String> punctuationCandidates) {
        pinyinRow.removeAllViews();
        candidateRow.removeAllViews();

        boolean hasCandidates = punctuationCandidates != null && !punctuationCandidates.isEmpty();
        if (!hasCandidates) {
            setCandidateRows(Collections.emptyList(), Collections.emptyList(), false);
            return;
        }

        setVisibility(View.VISIBLE);
        pinyinScroll.setVisibility(View.VISIBLE);
        rowDivider.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);
        expandButton.setVisibility(View.VISIBLE);
        setExpanded(false);

        for (String candidate : punctuationCandidates) {
            addCandidateText(candidate, true);
        }
        pinyinScroll.scrollTo(0, 0);
        candidateScroll.scrollTo(0, 0);
    }

    private void addPinyinText(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(17);
        view.setTextColor(IosKeyboardTheme.TEXT_PRIMARY);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(13), 0, dp(13), 0);
        view.setMinWidth(dp(34));
        view.setOnClickListener(v -> {
            if (listener != null) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onPinyinCandidate(text);
            }
        });
        pinyinRow.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private void addCandidateText(String text, boolean composing) {
        String displayText = displayText(text);
        TextView view = new TextView(getContext());
        view.setText(displayText);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(composing ? 20f : 20f);
        view.setTextColor(IosKeyboardTheme.TEXT_PRIMARY);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(11), 0, dp(11), 0);
        view.setMinWidth(dp(42));
        view.setOnClickListener(v -> {
            if (listener != null) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onCandidate(displayText);
            }
        });
        candidateRow.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private String displayText(String text) {
        if (text == null) {
            return "";
        }
        int separator = text.indexOf(CANDIDATE_SEPARATOR);
        return separator >= 0 ? text.substring(0, separator) : text;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

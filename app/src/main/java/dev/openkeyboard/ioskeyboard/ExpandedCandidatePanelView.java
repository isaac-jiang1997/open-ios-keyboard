package dev.openkeyboard.ioskeyboard;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

public final class ExpandedCandidatePanelView extends ScrollView {
    private static final char CANDIDATE_SEPARATOR = '\u001f';
    private static final int COLUMN_COUNT = 6;
    private static final int COLLAPSED_ROW_COUNT = 6;

    interface Listener {
        void onExpandedCandidate(String text);
    }

    private final LinearLayout rows;
    private Listener listener;

    public ExpandedCandidatePanelView(Context context) {
        super(context);
        setFillViewport(false);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);

        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);
        rows.setPadding(0, 0, 0, dp(8));
        addView(rows, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        setCandidates(Collections.emptyList());
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setCandidates(List<String> candidates) {
        setCandidates(candidates, COLLAPSED_ROW_COUNT);
    }

    void setCandidates(List<String> candidates, int startIndex) {
        rows.removeAllViews();
        if (candidates == null || candidates.size() <= startIndex) {
            scrollTo(0, 0);
            return;
        }
        int index = Math.max(0, startIndex);
        while (index < candidates.size()) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, 0);
            for (int column = 0; column < COLUMN_COUNT; column++) {
                String candidate = index < candidates.size() ? candidates.get(index) : "";
                row.addView(candidateView(candidate), new LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f));
                index++;
            }
            rows.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)));
            if (index < candidates.size()) {
                rows.addView(divider(), new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)));
            }
        }
        scrollTo(0, 0);
    }

    private TextView candidateView(String candidate) {
        String displayText = displayText(candidate);
        TextView view = new TextView(getContext());
        view.setText(displayText);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(IosKeyboardTheme.TEXT_PRIMARY);
        view.setTextSize(22);
        view.setIncludeFontPadding(false);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setPadding(dp(3), 0, dp(3), 0);
        view.setOnClickListener(v -> {
            if (listener != null && !displayText.isEmpty()) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onExpandedCandidate(displayText);
            }
        });
        return view;
    }

    private android.view.View divider() {
        android.view.View divider = new android.view.View(getContext());
        divider.setBackgroundColor(Color.rgb(177, 182, 190));
        return divider;
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

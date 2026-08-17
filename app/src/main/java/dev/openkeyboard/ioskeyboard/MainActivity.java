package dev.openkeyboard.ioskeyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private UserDictionaryStore userDictionaryStore;
    private LinearLayout phrasesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userDictionaryStore = new UserDictionaryStore(this);

        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(getColor(R.color.onboarding_bg));

        TextView title = new TextView(this);
        title.setText(R.string.onboarding_title);
        title.setTextColor(getColor(R.color.onboarding_text));
        title.setTextSize(30);
        title.setGravity(Gravity.START);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText(R.string.onboarding_body);
        body.setTextColor(getColor(R.color.onboarding_subtle));
        body.setTextSize(17);
        body.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(12), 0, dp(28));
        root.addView(body, bodyParams);

        Button enable = new Button(this);
        enable.setText(R.string.enable_keyboard);
        enable.setAllCaps(false);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)));

        Button switcher = new Button(this);
        switcher.setText(R.string.switch_keyboard);
        switcher.setAllCaps(false);
        switcher.setOnClickListener(v -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        switchParams.setMargins(0, dp(12), 0, 0);
        root.addView(switcher, switchParams);

        TextView privacy = new TextView(this);
        privacy.setText(R.string.privacy_note);
        privacy.setTextColor(getColor(R.color.onboarding_subtle));
        privacy.setTextSize(14);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        privacyParams.setMargins(0, dp(16), 0, dp(8));
        root.addView(privacy, privacyParams);

        TextView license = new TextView(this);
        license.setText(R.string.license_note);
        license.setTextColor(getColor(R.color.onboarding_subtle));
        license.setTextSize(14);
        LinearLayout.LayoutParams licenseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        licenseParams.setMargins(0, 0, 0, dp(24));
        root.addView(license, licenseParams);

        TextView phrasesTitle = new TextView(this);
        phrasesTitle.setText(R.string.custom_phrase_title);
        phrasesTitle.setTextColor(getColor(R.color.onboarding_text));
        phrasesTitle.setTextSize(22);
        phrasesTitle.setTypeface(phrasesTitle.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(phrasesTitle);

        EditText pinyin = new EditText(this);
        pinyin.setSingleLine(true);
        pinyin.setHint(R.string.custom_phrase_pinyin_hint);
        root.addView(pinyin, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText phrase = new EditText(this);
        phrase.setSingleLine(true);
        phrase.setHint(R.string.custom_phrase_text_hint);
        root.addView(phrase, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button addPhrase = new Button(this);
        addPhrase.setText(R.string.add_phrase);
        addPhrase.setAllCaps(false);
        addPhrase.setOnClickListener(v -> {
            userDictionaryStore.addCustomPhrase(
                    pinyin.getText().toString(),
                    phrase.getText().toString());
            pinyin.setText("");
            phrase.setText("");
            refreshPhraseList();
        });
        root.addView(addPhrase, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)));

        Button clear = new Button(this);
        clear.setText(R.string.clear_local_data);
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> confirmClearLocalData());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        clearParams.setMargins(0, dp(10), 0, 0);
        root.addView(clear, clearParams);

        phrasesList = new LinearLayout(this);
        phrasesList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        listParams.setMargins(0, dp(12), 0, 0);
        root.addView(phrasesList, listParams);
        refreshPhraseList();

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void refreshPhraseList() {
        if (phrasesList == null) {
            return;
        }
        phrasesList.removeAllViews();
        for (String row : userDictionaryStore.describeCustomPhrases()) {
            TextView item = new TextView(this);
            item.setText(row);
            item.setTextColor(getColor(R.color.onboarding_text));
            item.setTextSize(15);
            item.setPadding(0, dp(4), 0, dp(4));
            phrasesList.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void confirmClearLocalData() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_local_data)
                .setMessage(R.string.clear_local_data_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear_local_data_confirm_action, (dialog, which) -> clearLocalData())
                .show();
    }

    private void clearLocalData() {
        boolean prefsCleared = userDictionaryStore.clearAll();
        boolean rimeCleared = RimeChineseInputEngine.clearUserData(this);
        refreshPhraseList();
        int message = prefsCleared && rimeCleared
                ? R.string.clear_local_data_done
                : R.string.clear_local_data_failed;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

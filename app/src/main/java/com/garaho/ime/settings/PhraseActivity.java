package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.user.PhraseStore;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Canned-phrase manager (design doc §2.4). D-Pad list of label/text entries
 * with add / edit / delete. Selecting an entry copies its text to the clipboard
 * (full IME-input insertion via a symbol-panel tab is a follow-up).
 */
public class PhraseActivity extends BaseMenuActivity {

    private static final String[] CATEGORIES = { "邮箱", "问候", "个人", "其它" };

    private PhraseStore store;
    private List<PhraseStore.Entry> snapshot = new ArrayList<>();

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = PhraseStore.get(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        snapshot = store.entries();
        String[] items = new String[snapshot.size() + 1];
        items[0] = "＋ " + getString(R.string.phrase_add);
        for (int i = 0; i < snapshot.size(); i++) {
            PhraseStore.Entry e = snapshot.get(i);
            items[i + 1] = (e.label.isEmpty() ? e.text : e.label) + "  [" + e.category + "]";
        }
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    showPhraseDialog(-1, null);
                } else {
                    showPhraseDialog(position - 1, snapshot.get(position - 1));
                }
            }
        });
    }

    private void showPhraseDialog(final int editIndex, final PhraseStore.Entry existing) {
        final boolean editing = editIndex >= 0;
        View body = LayoutInflater.from(this).inflate(R.layout.dialog_two_fields, null);
        final EditText labelField = body.findViewById(R.id.field1);
        final EditText textField = body.findViewById(R.id.field2);
        labelField.setHint(R.string.phrase_label_hint);
        textField.setHint(R.string.phrase_text_hint);
        textField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (editing) {
            labelField.setText(existing.label);
            textField.setText(existing.text);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(editing ? R.string.phrase_edit : R.string.phrase_add)
                .setView(body)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String label = labelField.getText().toString().trim();
                        String text = textField.getText().toString().trim();
                        if (label.isEmpty() && text.isEmpty()) {
                            return;
                        }
                        String category = editing ? existing.category : CATEGORIES[0];
                        if (editing) {
                            store.update(editIndex, category, label, text);
                        } else {
                            store.add(category, label, text);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (editing) {
            b.setNeutralButton(R.string.user_dict_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    store.remove(editIndex);
                }
            });
            b.setPositiveButton(R.string.phrase_copy, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    copyToClipboard(existing.text);
                }
            });
        }
        b.show();
        labelField.requestFocus();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("WindIme", text));
        }
        Toast.makeText(this, R.string.phrase_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_phrase;
    }

    @Override
    protected int getHintRes() {
        return R.string.menu_hint_nav;
    }
}

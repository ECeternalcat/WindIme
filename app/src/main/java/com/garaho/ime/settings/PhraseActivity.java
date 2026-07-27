package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.user.PhraseStore;
import com.garaho.ime.user.StoreResult;

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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Canned-phrase manager (design doc §2.4). D-Pad list of label/text entries
 * with add / edit / delete / copy, plus import/export (improvement doc §5).
 *
 * <p>Edit mode exposes Save (positive), Copy (neutral) and Delete (negative) at
 * once; earlier versions let Copy overwrite Save, making edits impossible.
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

    private File exportFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = new File(getFilesDir(), "export");
        }
        return new File(dir, PhraseStore.EXPORT_FILE_NAME);
    }

    private void rebuild() {
        snapshot = store.entries();
        int size = snapshot.size();
        String[] items = new String[size + 3];
        items[0] = "＋ " + getString(R.string.phrase_add);
        for (int i = 0; i < size; i++) {
            PhraseStore.Entry e = snapshot.get(i);
            items[i + 1] = (e.label.isEmpty() ? e.text : e.label) + "  [" + e.category + "]";
        }
        items[size + 1] = "⬆ " + getString(R.string.phrase_export);
        items[size + 2] = "⬇ " + getString(R.string.phrase_import);
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    showPhraseDialog(-1, null);
                } else if (position == snapshot.size() + 1) {
                    doExport();
                } else if (position == snapshot.size() + 2) {
                    doImport();
                } else {
                    showPhraseDialog(position - 1, snapshot.get(position - 1));
                }
            }
        });
    }

    private void doExport() {
        File dest = exportFile();
        if (store.exportTo(dest)) {
            Toast.makeText(this, getString(R.string.store_export_success, dest.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.store_export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void doImport() {
        int added = store.importFrom(exportFile());
        if (added < 0) {
            Toast.makeText(this, R.string.store_import_failed, Toast.LENGTH_LONG).show();
        } else if (added == 0) {
            Toast.makeText(this, R.string.store_import_none, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.store_import_success, added),
                    Toast.LENGTH_SHORT).show();
        }
        rebuild();
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
                .setPositiveButton(editing ? R.string.phrase_save : R.string.phrase_add,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String label = labelField.getText().toString().trim();
                                String text = textField.getText().toString().trim();
                                String category = editing ? existing.category : CATEGORIES[0];
                                StoreResult r = editing
                                        ? store.update(editIndex, category, label, text)
                                        : store.add(category, label, text);
                                if (r != StoreResult.OK) {
                                    Toast.makeText(PhraseActivity.this, messageFor(r),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
        if (editing) {
            // Save stays positive; Copy and Delete get their own buttons so they
            // no longer overwrite Save (improvement doc §5). Cancel via BACK.
            b.setNeutralButton(R.string.phrase_copy, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String t = textField.getText().toString().trim();
                    copyToClipboard(t.isEmpty() ? existing.text : t);
                }
            });
            b.setNegativeButton(R.string.user_dict_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    store.remove(editIndex);
                }
            });
        } else {
            b.setNegativeButton(android.R.string.cancel, null);
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

    private int messageFor(StoreResult r) {
        switch (r) {
            case EMPTY: return R.string.store_validation_empty;
            case TOO_LONG: return R.string.store_validation_too_long;
            case DUPLICATE: return R.string.store_validation_duplicate;
            case IO_ERROR: return R.string.store_validation_io;
            default: return R.string.store_validation_io;
        }
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_phrase;
    }

}

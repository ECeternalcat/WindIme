package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.user.StoreResult;
import com.garaho.ime.user.UserDictionary;

import android.app.AlertDialog;
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
 * User-dictionary manager (design doc §2.3). D-Pad list of pinyin &rarr; word
 * entries with add / edit / delete via AlertDialogs (which are themselves
 * focus-navigable), plus import/export of the JSON store to app-specific
 * external storage (improvement doc §5). Added words are surfaced by the
 * pinyin engines via {@link UserDictionary} (a {@link com.garaho.ime.user.UserWordSource}).
 */
public class UserDictActivity extends BaseMenuActivity {

    private UserDictionary dict;
    private List<UserDictionary.Entry> snapshot = new ArrayList<>();

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dict = UserDictionary.get(this);
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
        return new File(dir, UserDictionary.EXPORT_FILE_NAME);
    }

    private void rebuild() {
        snapshot = dict.entries();
        int size = snapshot.size();
        String[] items = new String[size + 3];
        items[0] = "＋ " + getString(R.string.user_dict_add);
        for (int i = 0; i < size; i++) {
            UserDictionary.Entry e = snapshot.get(i);
            items[i + 1] = e.word + "  [" + e.pinyin + "]";
        }
        items[size + 1] = "⬆ " + getString(R.string.user_dict_export);
        items[size + 2] = "⬇ " + getString(R.string.user_dict_import);
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    showEntryDialog(null, null, false);
                } else if (position == snapshot.size() + 1) {
                    doExport();
                } else if (position == snapshot.size() + 2) {
                    doImport();
                } else {
                    UserDictionary.Entry e = snapshot.get(position - 1);
                    showEntryDialog(e.pinyin, e.word, true);
                }
            }
        });
    }

    private void doExport() {
        File dest = exportFile();
        if (dict.exportTo(dest)) {
            Toast.makeText(this, getString(R.string.store_export_success, dest.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.store_export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void doImport() {
        int added = dict.importFrom(exportFile());
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

    private void showEntryDialog(final String oldPinyin, final String oldWord, final boolean editing) {
        View body = LayoutInflater.from(this).inflate(R.layout.dialog_two_fields, null);
        final EditText pinyinField = body.findViewById(R.id.field1);
        final EditText wordField = body.findViewById(R.id.field2);
        pinyinField.setHint(R.string.user_dict_pinyin_hint);
        pinyinField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        wordField.setHint(R.string.user_dict_word_hint);
        if (editing) {
            pinyinField.setText(oldPinyin);
            wordField.setText(oldWord);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(editing ? R.string.user_dict_edit : R.string.user_dict_add)
                .setView(body)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String p = pinyinField.getText().toString().trim();
                        String w = wordField.getText().toString().trim();
                        if (editing) {
                            dict.remove(oldPinyin, oldWord);
                        }
                        StoreResult r = dict.add(p, w);
                        if (r != StoreResult.OK) {
                            // Roll back the removal so a failed edit never loses
                            // the original entry.
                            if (editing) {
                                dict.add(oldPinyin, oldWord);
                            }
                            Toast.makeText(UserDictActivity.this, messageFor(r),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (editing) {
            b.setNeutralButton(R.string.user_dict_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dict.remove(oldPinyin, oldWord);
                }
            });
        }
        b.show();
        pinyinField.requestFocus();
    }

    private int messageFor(StoreResult r) {
        switch (r) {
            case EMPTY: return R.string.store_validation_empty;
            case TOO_LONG: return R.string.store_validation_too_long;
            case TOO_MANY: return R.string.store_validation_too_many;
            case DUPLICATE: return R.string.store_validation_duplicate;
            case IO_ERROR: return R.string.store_validation_io;
            default: return R.string.store_validation_io;
        }
    }

    @Override
    protected int getTitleRes() {
        return R.string.settings_user_dict;
    }

    @Override
    protected int getHintRes() {
        return R.string.menu_hint_nav;
    }
}

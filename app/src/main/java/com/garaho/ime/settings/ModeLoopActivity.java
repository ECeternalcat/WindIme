package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.engine.InputMode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mode-loop selector (design doc §1.2). Multi-select of the modes that
 * TOGGLE_LANG_MODE cycles through. At least one mode must remain enabled.
 */
public class ModeLoopActivity extends Activity {

    private GarahoPrefs prefs;
    private final List<InputMode> allModes = new ArrayList<>();
    private final Set<InputMode> selected = new LinkedHashSet<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        prefs = new GarahoPrefs(this);
        ((android.widget.TextView) findViewById(R.id.menu_title)).setText(R.string.input_mode_loop);

        allModes.add(InputMode.ZH);
        allModes.add(InputMode.ZH_MTAP);
        allModes.add(InputMode.EN);
        allModes.add(InputMode.EN_MTAP);
        allModes.add(InputMode.NUM);
        selected.addAll(prefs.getModeLoop());

        ListView list = findViewById(R.id.menu_list);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        list.setItemsCanFocus(false);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, labels());
        list.setAdapter(adapter);
        for (int i = 0; i < allModes.size(); i++) {
            list.setItemChecked(i, selected.contains(allModes.get(i)));
        }
        list.setSelection(0);
        list.requestFocus();
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                InputMode m = allModes.get(position);
                boolean nowChecked = ((ListView) parent).isItemChecked(position);
                if (nowChecked) {
                    selected.add(m);
                } else if (selected.size() > 1) {
                    selected.remove(m);
                } else {
                    // keep at least one - re-check
                    ((ListView) parent).setItemChecked(position, true);
                    warnKeepOne();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        persist();
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
    }

    private void persist() {
        List<InputMode> ordered = new ArrayList<>();
        for (InputMode m : allModes) {
            if (selected.contains(m)) {
                ordered.add(m);
            }
        }
        if (ordered.isEmpty()) {
            ordered.add(InputMode.ZH);
        }
        prefs.setModeLoop(ordered);
    }

    private List<String> labels() {
        List<String> out = new ArrayList<>();
        for (InputMode m : allModes) {
            out.add(modeLabel(m));
        }
        return out;
    }

    private String modeLabel(InputMode m) {
        switch (m) {
            case ZH: return getString(R.string.mode_zh_t9);
            case ZH_MTAP: return getString(R.string.mode_zh_mtap);
            case EN: return getString(R.string.mode_en_t9);
            case EN_MTAP: return getString(R.string.mode_en_mtap);
            case NUM: return getString(R.string.mode_num);
            default: return m.name();
        }
    }

    private void warnKeepOne() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.mode_loop_keep_one)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
}

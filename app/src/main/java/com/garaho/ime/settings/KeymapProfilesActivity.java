package com.garaho.ime.settings;

import com.garaho.ime.R;
import com.garaho.ime.keymap.InputAction;
import com.garaho.ime.keymap.KeyMapConfig;
import com.garaho.ime.keymap.KeyMapper;
import com.garaho.ime.keymap.KeymapSlots;
import com.garaho.ime.ui.SetupWizardActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

/** Selects, inspects and manages the factory map and four user profile slots. */
public class KeymapProfilesActivity extends BaseMenuActivity {

    public static final String EXTRA_CALIBRATION_PICKER = "calibration_picker";

    private KeyMapper mapper;
    private GarahoPrefs prefs;
    private boolean calibrationPicker;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new GarahoPrefs(this);
        calibrationPicker = getIntent().getBooleanExtra(EXTRA_CALIBRATION_PICKER, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapper = new KeyMapper(this);
        rebuild();
    }

    private void rebuild() {
        int active = mapper.getActiveSlot();
        String[] items = new String[KeymapSlots.USER_MAX + 1];
        for (int slot = KeymapSlots.FACTORY; slot <= KeymapSlots.USER_MAX; slot++) {
            String name = slotName(this, slot);
            if (KeymapSlots.isUser(slot) && !mapper.isSlotConfigured(slot)) {
                name = getString(R.string.keymap_slot_empty, name);
            }
            items[slot] = getString(slot == active
                    ? R.string.keymap_slot_active : R.string.keymap_slot_inactive, name);
        }
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                handleSlot(position);
            }
        });
    }

    private void handleSlot(final int slot) {
        if (calibrationPicker) {
            if (slot == KeymapSlots.FACTORY) {
                showMessage(R.string.keymap_factory_read_only);
            } else {
                confirmCalibration(slot);
            }
            return;
        }
        if (slot == KeymapSlots.FACTORY) {
            showFactoryActions();
        } else if (!mapper.isSlotConfigured(slot)) {
            launchCalibration(slot);
        } else {
            showUserActions(slot);
        }
    }

    private void showFactoryActions() {
        final String[] actions = {
                getString(R.string.keymap_slot_activate),
                getString(R.string.keymap_slot_view),
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.keymap_factory_slot)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            mapper.activateSlot(KeymapSlots.FACTORY);
                            rebuild();
                        } else {
                            showMappingDetails(KeymapSlots.FACTORY);
                        }
                    }
                }).show();
    }

    private void showUserActions(final int slot) {
        final String[] actions = {
                getString(R.string.keymap_slot_activate),
                getString(R.string.keymap_slot_view),
                getString(R.string.keymap_slot_calibrate),
                getString(R.string.keymap_slot_rename),
                getString(R.string.keymap_slot_delete),
        };
        new AlertDialog.Builder(this)
                .setTitle(slotName(this, slot))
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            mapper.activateSlot(slot);
                            rebuild();
                        } else if (which == 1) {
                            showMappingDetails(slot);
                        } else if (which == 2) {
                            launchCalibration(slot);
                        } else if (which == 3) {
                            showRenameDialog(slot);
                        } else {
                            confirmDelete(slot);
                        }
                    }
                }).show();
    }

    private void confirmCalibration(final int slot) {
        if (!mapper.isSlotConfigured(slot)) {
            launchCalibration(slot);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.keymap_slot_overwrite_confirm, slotName(this, slot)))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        launchCalibration(slot);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void launchCalibration(int slot) {
        startActivity(new Intent(this, com.garaho.ime.ui.SetupWizardIntroActivity.class)
                .putExtra(SetupWizardActivity.EXTRA_TARGET_SLOT, slot));
    }

    private void showMappingDetails(int slot) {
        KeyMapConfig config = mapper.loadSlotConfig(slot);
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.keymap_detail_source)).append(": ")
                .append(slot == KeymapSlots.FACTORY
                        ? getString(R.string.keymap_source_factory)
                        : getString(R.string.keymap_source_user)).append("\n\n");
        appendMapping(text, config, InputAction.TOGGLE_LANG_MODE, R.string.action_lang_mode);
        appendMapping(text, config, InputAction.SHOW_SYMBOL_PANEL, R.string.action_symbol_panel);
        appendMapping(text, config, InputAction.SHOW_QUICK_MENU, R.string.action_quick_menu);
        appendMapping(text, config, InputAction.BACKSPACE_DELETE, R.string.action_backspace);
        appendMapping(text, config, InputAction.ENTER, R.string.action_enter);
        appendMapping(text, config, InputAction.TOGGLE_CAPS, R.string.action_caps);
        appendMapping(text, config, InputAction.DISMISS_IME, R.string.action_dismiss);
        appendMapping(text, config, InputAction.SOFTKEY_LEFT, R.string.action_softkey_left);
        appendMapping(text, config, InputAction.SOFTKEY_RIGHT, R.string.action_softkey_right);
        new AlertDialog.Builder(this)
                .setTitle(slotName(this, slot))
                .setMessage(text.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void appendMapping(StringBuilder text, KeyMapConfig config, InputAction action,
            int labelRes) {
        KeyMapConfig.Mapping found = null;
        if (config != null) {
            for (KeyMapConfig.Mapping mapping : config.mappings) {
                if (mapping.action == action) {
                    found = mapping;
                    break;
                }
            }
        }
        text.append(getString(labelRes)).append(": ");
        if (found == null) {
            text.append(getString(R.string.keymap_unbound));
        } else {
            text.append(KeyEvent.keyCodeToString(found.keycode))
                    .append("\nKeyCode: ").append(found.keycode)
                    .append("  ScanCode: ").append(found.scanCode);
        }
        text.append("\n\n");
    }

    private void showRenameDialog(final int slot) {
        View body = LayoutInflater.from(this).inflate(R.layout.dialog_keymap_name, null);
        final EditText field = body.findViewById(R.id.keymap_name);
        field.setText(slotName(this, slot));
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.keymap_slot_rename)
                .setView(body)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = field.getText().toString().trim();
                    if (name.isEmpty()) {
                        field.setError(getString(R.string.keymap_name_required));
                        return;
                    }
                    prefs.setKeymapSlotName(slot, name);
                    dialog.dismiss();
                    rebuild();
                }));
        dialog.show();
        field.requestFocus();
        field.selectAll();
    }

    private void confirmDelete(final int slot) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.keymap_slot_delete_confirm, slotName(this, slot)))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        mapper.clearUserSlot(slot);
                        rebuild();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMessage(int messageRes) {
        new AlertDialog.Builder(this)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static String slotName(Context context, int slot) {
        if (slot == KeymapSlots.FACTORY) {
            return context.getString(R.string.keymap_factory_slot);
        }
        GarahoPrefs prefs = new GarahoPrefs(context);
        String name = prefs.getKeymapSlotName(slot);
        return name == null || name.trim().isEmpty()
                ? context.getString(R.string.keymap_user_slot_default, slot) : name;
    }

    @Override
    protected int getTitleRes() {
        return calibrationPicker ? R.string.keymap_choose_target : R.string.keymap_profiles_title;
    }
}

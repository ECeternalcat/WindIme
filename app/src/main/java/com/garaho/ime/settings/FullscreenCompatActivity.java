package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.view.View;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fullscreen-extract compatibility list (np701kc.md §15).
 *
 * <p>Lists the host packages for which the IME opts into the framework's
 * native fullscreen/extract layout (because the framework blanks those apps'
 * own EditText while typing). Only reachable on Kyocera devices
 * ({@code SoftkeyGuideHelper.create() != null}). The top row is an explanatory
 * notice; the rest are the configured packages (OK removes) plus an
 * "add last used app" row. Defaults to Notepad.
 */
public class FullscreenCompatActivity extends BaseMenuActivity {

    private GarahoPrefs prefs;

    @Override
    protected int getTitleRes() {
        return R.string.fullscreen_compat_title;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new GarahoPrefs(this);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        final List<String> pkgOrder = new ArrayList<>(prefs.getFullscreenCompatPackages());
        final String last = prefs.getLastHostPackage();
        final boolean canAdd = last != null && !last.isEmpty() && !pkgOrder.contains(last);

        List<String> items = new ArrayList<>();
        items.add(getString(R.string.fullscreen_compat_notice));
        String removeSuffix = getString(R.string.fullscreen_compat_remove_suffix);
        for (String pkg : pkgOrder) {
            items.add(pkg + removeSuffix);
        }
        if (canAdd) {
            items.add(getString(R.string.fullscreen_compat_add_prefix) + last);
        }

        final int pkgCount = pkgOrder.size();
        setMenuItems(items.toArray(new String[0]), new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // Notice row: no-op.
                    return;
                }
                if (position >= 1 && position <= pkgCount) {
                    // Remove the selected package.
                    String pkg = pkgOrder.get(position - 1);
                    Set<String> next = new LinkedHashSet<>(prefs.getFullscreenCompatPackages());
                    next.remove(pkg);
                    prefs.setFullscreenCompatPackages(next);
                    rebuild();
                    return;
                }
                // "Add last used app" row (only shown when canAdd is true).
                if (canAdd) {
                    Set<String> next = new LinkedHashSet<>(prefs.getFullscreenCompatPackages());
                    next.add(last);
                    prefs.setFullscreenCompatPackages(next);
                    rebuild();
                }
            }
        });
    }
}

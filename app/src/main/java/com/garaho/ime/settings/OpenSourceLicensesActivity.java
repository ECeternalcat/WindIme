package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

/** Lists the open-source components shipped in the installed application. */
public class OpenSourceLicensesActivity extends BaseMenuActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] items = new String[] {
                getString(R.string.license_windime),
                getString(R.string.license_rime),
                getString(R.string.license_trime),
                getString(R.string.license_rime_ice),
                getString(R.string.license_androidx),
        };
        setMenuItems(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                boolean rime = position == 1;
                boolean apache = position == 4;
                Intent intent = new Intent(OpenSourceLicensesActivity.this,
                        LicenseTextActivity.class);
                intent.putExtra(LicenseTextActivity.EXTRA_TITLE, items[position]);
                intent.putExtra(LicenseTextActivity.EXTRA_ASSET,
                        rime ? "licenses/LICENSE.librime.txt"
                                : apache ? "licenses/LICENSE.apache-2.0.txt"
                                : "rime/LICENSE.rime-ice.txt");
                intent.putExtra(LicenseTextActivity.EXTRA_ATTRIBUTION,
                        rime ? R.string.license_rime_attribution
                                : apache ? R.string.license_androidx_attribution
                                : R.string.license_gpl_attribution);
                startActivity(intent);
            }
        });
    }

    @Override
    protected int getTitleRes() {
        return R.string.about_open_source_licenses;
    }
}

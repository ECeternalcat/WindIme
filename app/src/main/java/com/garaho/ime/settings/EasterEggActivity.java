package com.garaho.ime.settings;

import com.garaho.ime.R;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.ScrollView;

import java.io.IOException;
import java.io.InputStream;

/** Hidden developer photo page reached by activating the About notice five times. */
public class EasterEggActivity extends Activity {

    private static final String IMAGE_ASSET = "Easter egg.JPG";
    private Bitmap imageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_easter_egg);

        ImageView image = findViewById(R.id.easter_egg_image);
        imageBitmap = loadScaledImage();
        image.setImageBitmap(imageBitmap);

        ScrollView scroll = findViewById(R.id.easter_egg_scroll);
        scroll.requestFocus();
    }

    private Bitmap loadScaledImage() {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getAssets().open(IMAGE_ASSET)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException e) {
            return null;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int targetWidth = Math.max(1, metrics.widthPixels);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / (options.inSampleSize * 2) >= targetWidth) {
            options.inSampleSize *= 2;
        }
        try (InputStream input = getAssets().open(IMAGE_ASSET)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imageBitmap != null) {
            imageBitmap.recycle();
            imageBitmap = null;
        }
    }
}

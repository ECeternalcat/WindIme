package com.garaho.ime.settings.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.garaho.ime.BuildConfig;
import com.garaho.ime.settings.GarahoPrefs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Checks WindIme's latest GitHub release and reports whether it is newer than
 * the running build. Version source of truth is the release tag
 * ({@code tag_name}); the APK asset is picked by {@code .apk} extension.
 *
 * <p>The unauthenticated GitHub API allows 60 requests/hour per IP, so the
 * silent auto-check is throttled to once per day (successful check marks the
 * timestamp; failures retry on the next visit). Manual checks from the about
 * page always run.
 */
public final class UpdateChecker {

    public static final String API_URL =
            "https://api.github.com/repos/ECeternalcat/WindIme/releases/latest";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final long AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    /** Result of a completed check. {@code release} is null when current. */
    public interface Callback {
        void onResult(UpdateInfo release, boolean isNewer);

        void onError();
    }

    private UpdateChecker() {
    }

    public static boolean shouldAutoCheck(GarahoPrefs prefs) {
        long last = prefs.getLastUpdateCheck();
        return last <= 0 || System.currentTimeMillis() - last >= AUTO_CHECK_INTERVAL_MS;
    }

    public static void checkAsync(final Context context, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UpdateInfo info = fetchLatest();
                    // Mark the throttle timestamp only on a completed check so
                    // a flaky network retries on the next settings visit.
                    new GarahoPrefs(context).setLastUpdateCheck(System.currentTimeMillis());
                    final boolean newer = info != null
                            && info.isNewerThan(BuildConfig.VERSION_NAME);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(info, newer);
                        }
                    });
                } catch (IOException e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError();
                        }
                    });
                }
            }
        }, "windime-update-check").start();
    }

    static UpdateInfo fetchLatest() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        try {
            if (!(conn instanceof HttpsURLConnection)) {
                throw new IOException("update check requires https");
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "WindIme-UpdateChecker");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            UpdateInfo info = GitHubReleaseParser.parse(readAll(conn.getInputStream()));
            if (info == null) {
                throw new IOException("unparsable release payload");
            }
            return info;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
}

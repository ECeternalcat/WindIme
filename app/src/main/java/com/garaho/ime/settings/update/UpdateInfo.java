package com.garaho.ime.settings.update;

/** One GitHub release, reduced to what the update flow needs. */
public final class UpdateInfo {

    /** Release tag exactly as published, e.g. {@code v0.5.6}. */
    public final String tagName;
    /** Release notes body (markdown as published). */
    public final String notes;
    /** Direct browser download URL of the {@code .apk} asset, or null. */
    public final String apkUrl;
    /** Human-facing release page URL; always present. */
    public final String htmlUrl;

    public UpdateInfo(String tagName, String notes, String apkUrl, String htmlUrl) {
        this.tagName = tagName == null ? "" : tagName;
        this.notes = notes == null ? "" : notes;
        this.apkUrl = apkUrl;
        this.htmlUrl = htmlUrl == null ? "" : htmlUrl;
    }

    /** Tag without the leading {@code v}, e.g. {@code 0.5.6}. */
    public String versionName() {
        return VersionUtil.stripPrefix(tagName);
    }

    public boolean isNewerThan(String currentVersion) {
        return VersionUtil.isNewer(versionName(), VersionUtil.stripPrefix(currentVersion));
    }

    /** Preferred URL to open in a browser: the APK asset, else the release page. */
    public String downloadUrl() {
        return apkUrl != null && !apkUrl.isEmpty() ? apkUrl : htmlUrl;
    }
}

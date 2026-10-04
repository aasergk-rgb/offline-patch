package app.revanced.extension.offline;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Redirects the in-app "Download" action of YouTube / YouTube Music
 * to an external downloader app, so the media can be saved and played offline
 * without a YouTube Premium subscription.
 */
@SuppressWarnings("unused")
public final class OfflineDownloadPatch {
    private static final String TAG = "offline-patch";

    /**
     * The package name of the external downloader app.
     * The return value is replaced at patch time by the "Downloader package name" patch option.
     */
    private static String getDownloaderPackageName() {
        return "";
    }

    /**
     * Injection point (YouTube).
     * Called when the in-app download button or the "Download" flyout menu item is used.
     *
     * @return If the download was handled and the original download logic must be skipped.
     */
    public static boolean onYouTubeDownload(String videoId) {
        return launchDownloader("https://youtu.be/" + videoId);
    }

    /**
     * Injection point (YouTube Music).
     * Called when a song / video is added to the downloads.
     *
     * @return If the download was handled and the original download logic must be skipped.
     */
    public static boolean onMusicDownload(String videoId) {
        return launchDownloader("https://music.youtube.com/watch?v=" + videoId);
    }

    private static boolean launchDownloader(String url) {
        try {
            if (url.endsWith("=") || url.endsWith("/")) {
                // No video id available. Let the app handle it.
                return false;
            }

            Context context = getContext();
            if (context == null) {
                Log.e(TAG, "Context is null, cannot launch the downloader");
                return false;
            }

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            // An application context is used, so the activity must be started in a new task.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            String packageName = getDownloaderPackageName().trim();
            if (!packageName.isEmpty()) {
                intent.setPackage(packageName);
                try {
                    context.startActivity(intent);
                    Log.d(TAG, "Launched " + packageName + " for " + url);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    Log.w(TAG, packageName + " is not installed, showing the app chooser instead");
                    showToast(context, packageName + " is not installed");
                    intent.setPackage(null);
                }
            }

            // No (installed) downloader configured: let the user pick one.
            Intent chooser = Intent.createChooser(intent, null);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to launch the downloader", ex);
            return false;
        }
    }

    private static void showToast(Context context, String message) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            // Not called from a looper thread.
            Log.w(TAG, "Failed to show toast", ex);
        }
    }

    private static Context getContext() {
        try {
            // Hidden API, but on the public "unsupported" (greylist) list and usable by apps.
            Application application = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            return application;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to get the application context", ex);
            return null;
        }
    }
}

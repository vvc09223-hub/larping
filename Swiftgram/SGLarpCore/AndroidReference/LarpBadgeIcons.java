package tw.nekomimi.nekogram.helpers;

import android.graphics.drawable.Drawable;

import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

/**
 * HOLD / Major icons from MajorVerification
 * https://t.me/addemoji/MajorVerification
 */
public final class LarpBadgeIcons {

    public static final String PACK_SHORT_NAME = "MajorVerification";
    public static final long PACK_ID = 3967798175112101917L;
    public static final long HOLD_DOCUMENT_ID = 5229027828527309057L;
    public static final long MAJOR_DOCUMENT_ID = 5440670060093922400L;

    private LarpBadgeIcons() {
    }

    public static void ensureLoaded(int account, Runnable whenReady) {
        if (whenReady != null) {
            whenReady.run();
        }
    }

    public static Drawable createHold(int account) {
        return AnimatedEmojiDrawable.make(
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_COLORABLE,
                HOLD_DOCUMENT_ID);
    }

    public static Drawable createMajor(int account) {
        return AnimatedEmojiDrawable.make(
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_COLORABLE,
                MAJOR_DOCUMENT_ID);
    }

    public static void preload(int account) {
        // Touch both drawables so documents start loading into cache.
        createHold(account);
        createMajor(account);
    }

    public static void preloadSelectedAccount() {
        preload(UserConfig.selectedAccount);
    }
}

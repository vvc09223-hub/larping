package tw.nekomimi.nekogram.helpers;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Stars.StarsController;

/**
 * [MG] Fake Stars / TON balance for LARP purchases.
 */
public final class LarpBalanceHelper {

    private static final long DEFAULT_STARS = 10_000_000L;
    private static final long DEFAULT_TON = 100L;

    private LarpBalanceHelper() {
    }

    public static long getStarsBalance() {
        if (!LicenseManager.fakeBalanceEnabled() && !LicenseManager.larpGiftsEnabled()) {
            return 0;
        }
        long amount = SharedConfig.fakeStarsAmount;
        if (amount <= 0 && LicenseManager.larpGiftsEnabled()) {
            amount = DEFAULT_STARS;
        }
        return Math.max(0, amount);
    }

    public static long getTonBalance() {
        if (!LicenseManager.fakeBalanceEnabled() && !LicenseManager.larpGiftsEnabled()) {
            return 0;
        }
        long amount = SharedConfig.fakeTonAmount;
        if (amount <= 0 && LicenseManager.larpGiftsEnabled()) {
            amount = DEFAULT_TON;
        }
        return Math.max(0, amount);
    }

    public static boolean spendStars(int currentAccount, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (!LicenseManager.larpGiftsEnabled()) {
            return false;
        }
        long balance = getStarsBalance();
        if (balance < amount) {
            return false;
        }
        SharedConfig.setFakeStarsAmount(balance - amount);
        notifyBalanceChanged(currentAccount, false);
        return true;
    }

    public static boolean spendTon(int currentAccount, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (!LicenseManager.larpGiftsEnabled()) {
            return false;
        }
        long balance = getTonBalance();
        if (balance < amount) {
            return false;
        }
        SharedConfig.setFakeTonAmount(balance - amount);
        notifyBalanceChanged(currentAccount, true);
        return true;
    }

    public static void notifyBalanceChanged(int currentAccount, boolean ton) {
        StarsController.getInstance(currentAccount, ton).invalidateBalance(null);
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.starBalanceUpdated);
    }
}

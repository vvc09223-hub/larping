package tw.nekomimi.nekogram.helpers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

/**
 * [MG] Local Stars / TON history entries for LARP gift spends.
 */
public final class LarpTransactionHelper {

    private static final String PREFS = "mg_larp_transactions";

    private static final String TYPE_GIFT = "gift";
    private static final String TYPE_TOPUP = "topup";

    private LarpTransactionHelper() {
    }

    public static void addStarsTopup(int currentAccount, long stars) {
        if (stars <= 0) {
            return;
        }
        final String label = LocaleController.getString(R.string.StarsTransactionInApp);
        final TL_stars.TL_starsTransaction tx = new TL_stars.TL_starsTransaction();
        tx.id = "mg-larp-topup-" + System.currentTimeMillis();
        tx.date = (int) (System.currentTimeMillis() / 1000L);
        tx.gift = false;
        tx.amount = TL_stars.StarsAmount.ofStars(stars);
        tx.peer = makeInAppPeer();
        tx.title = label;
        tx.description = label;
        tx.flags = TLObject.setFlag(tx.flags, 1, true);
        tx.flags = TLObject.setFlag(tx.flags, 2, true);

        persistTransaction(currentAccount, false, tx, 0, TYPE_TOPUP);
        final StarsController controller = StarsController.getInstance(currentAccount, false);
        AndroidUtilities.runOnUIThread(() -> {
            if (!containsTx(controller, tx.id)) {
                controller.transactions[StarsController.ALL_TRANSACTIONS].add(0, tx);
                controller.transactions[StarsController.INCOMING_TRANSACTIONS].add(0, tx);
            }
            controller.transactionsExist[StarsController.ALL_TRANSACTIONS] = true;
            controller.transactionsExist[StarsController.INCOMING_TRANSACTIONS] = true;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.starTransactionsLoaded);
        });
    }

    public static void addGiftSpend(
            int currentAccount,
            boolean ton,
            long price,
            TLRPC.Peer counterparty,
            TL_stars.StarGift gift,
            boolean resale,
            String description) {
        if (!LicenseManager.larpGiftsEnabled()) {
            return;
        }
        final TL_stars.TL_starsTransaction tx = new TL_stars.TL_starsTransaction();
        tx.id = "mg-larp-" + System.currentTimeMillis();
        tx.date = (int) (System.currentTimeMillis() / 1000L);
        tx.gift = true;
        tx.flags = TLObject.setFlag(tx.flags, TLObject.FLAG_10, true);
        tx.flags = TLObject.setFlag(tx.flags, TLObject.FLAG_14, gift != null);
        if (resale) {
            tx.stargift_resale = true;
            tx.flags = TLObject.setFlag(tx.flags, TLObject.FLAG_22, true);
        }
        if (price > 0) {
            if (ton) {
                final TL_stars.TL_starsTonAmount tonAmount = new TL_stars.TL_starsTonAmount();
                tonAmount.amount = -price;
                tx.amount = tonAmount;
            } else {
                tx.amount = TL_stars.StarsAmount.ofStars(-price);
            }
        } else {
            tx.amount = TL_stars.StarsAmount.ofStars(0);
        }
        long peerDialogId = DialogObject.getPeerDialogId(counterparty);
        if (peerDialogId == 0) {
            peerDialogId = UserConfig.getInstance(currentAccount).getClientUserId();
        }
        tx.peer = makeTxPeer(peerDialogId);
        if (gift != null) {
            tx.stargift = gift;
            tx.title = gift.title != null ? gift.title : "Star Gift";
        } else {
            tx.title = ton ? "TON Gift" : "Star Gift";
        }
        tx.flags = TLObject.setFlag(tx.flags, 1, true);
        if (description != null) {
            tx.description = description;
            tx.flags = TLObject.setFlag(tx.flags, 2, true);
        }

        persistTransaction(currentAccount, ton, tx, peerDialogId, TYPE_GIFT);
        final StarsController controller = StarsController.getInstance(currentAccount, ton);
        AndroidUtilities.runOnUIThread(() -> {
            if (!containsTx(controller, tx.id)) {
                controller.transactions[StarsController.ALL_TRANSACTIONS].add(0, tx);
                if (price > 0) {
                    controller.transactions[StarsController.OUTGOING_TRANSACTIONS].add(0, tx);
                }
            }
            controller.transactionsExist[StarsController.ALL_TRANSACTIONS] = true;
            controller.transactionsExist[StarsController.OUTGOING_TRANSACTIONS] = true;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.starTransactionsLoaded);
        });
    }

    public static void mergeStored(int currentAccount, boolean ton) {
        final StarsController controller = StarsController.getInstance(currentAccount, ton);
        boolean changed = false;
        for (TL_stars.StarsTransaction tx : loadStored(currentAccount, ton)) {
            if (tx != null && !containsTx(controller, tx.id)) {
                controller.transactions[StarsController.ALL_TRANSACTIONS].add(0, tx);
                if (tx.amount != null && tx.amount.amount > 0) {
                    controller.transactions[StarsController.INCOMING_TRANSACTIONS].add(0, tx);
                } else if (tx.amount != null && tx.amount.amount < 0) {
                    controller.transactions[StarsController.OUTGOING_TRANSACTIONS].add(0, tx);
                }
                changed = true;
            }
        }
        if (changed) {
            controller.transactionsExist[StarsController.ALL_TRANSACTIONS] = true;
            controller.transactionsExist[StarsController.INCOMING_TRANSACTIONS] = true;
            controller.transactionsExist[StarsController.OUTGOING_TRANSACTIONS] = true;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.starTransactionsLoaded);
        }
    }

    private static boolean containsTx(StarsController controller, String id) {
        if (id == null) {
            return false;
        }
        for (TL_stars.StarsTransaction t : controller.transactions[StarsController.ALL_TRANSACTIONS]) {
            if (t != null && id.equals(t.id)) {
                return true;
            }
        }
        return false;
    }

    private static TL_stars.TL_starsTransactionPeer makeTxPeer(long dialogId) {
        final TL_stars.TL_starsTransactionPeer txPeer = new TL_stars.TL_starsTransactionPeer();
        final int account = UserConfig.selectedAccount;
        if (dialogId == 0) {
            dialogId = UserConfig.getInstance(account).getClientUserId();
        }
        TLRPC.Peer peer = MessagesController.getInstance(account).getPeer(dialogId);
        if (peer == null && dialogId > 0) {
            final TLRPC.TL_peerUser user = new TLRPC.TL_peerUser();
            user.user_id = dialogId;
            peer = user;
        }
        txPeer.peer = peer;
        return txPeer;
    }

    private static TL_stars.StarsTransactionPeer makeInAppPeer() {
        return new TL_stars.TL_starsTransactionPeerPlayMarket();
    }

    private static void persistTransaction(
            int currentAccount,
            boolean ton,
            TL_stars.TL_starsTransaction tx,
            long peerDialogId,
            String type) {
        try {
            final JSONArray arr = loadJsonArray(currentAccount, ton);
            final JSONObject o = new JSONObject();
            o.put("id", tx.id);
            o.put("date", tx.date);
            o.put("type", type);
            o.put("title", tx.title);
            o.put("description", tx.description != null ? tx.description : "");
            o.put("gift", tx.gift);
            o.put("resale", tx.stargift_resale);
            o.put("amount", tx.amount != null ? tx.amount.amount : 0);
            o.put("ton", ton);
            o.put("peerDialogId", peerDialogId);
            arr.put(0, o);
            while (arr.length() > 200) {
                arr.remove(arr.length() - 1);
            }
            saveJsonArray(currentAccount, ton, arr);
        } catch (Exception ignored) {
        }
    }

    private static java.util.ArrayList<TL_stars.StarsTransaction> loadStored(int currentAccount, boolean ton) {
        final java.util.ArrayList<TL_stars.StarsTransaction> list = new java.util.ArrayList<>();
        try {
            final JSONArray arr = loadJsonArray(currentAccount, ton);
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                final String type = o.optString("type", TYPE_GIFT);
                final TL_stars.TL_starsTransaction tx = new TL_stars.TL_starsTransaction();
                tx.id = o.optString("id", "");
                tx.date = o.optInt("date", 0);
                tx.gift = TYPE_GIFT.equals(type) && o.optBoolean("gift", true);
                tx.title = o.optString("title", TYPE_TOPUP.equals(type)
                        ? LocaleController.getString(R.string.StarsTransactionInApp)
                        : "Gift");
                tx.description = o.optString("description", "");
                tx.stargift_resale = o.optBoolean("resale", false);
                final long amount = o.optLong("amount", 0);
                if (o.optBoolean("ton", false)) {
                    final TL_stars.TL_starsTonAmount tonAmount = new TL_stars.TL_starsTonAmount();
                    tonAmount.amount = amount;
                    tx.amount = tonAmount;
                } else {
                    tx.amount = TL_stars.StarsAmount.ofStars(amount);
                }
                tx.flags = TLObject.setFlag(tx.flags, 1, true);
                if (!tx.description.isEmpty()) {
                    tx.flags = TLObject.setFlag(tx.flags, 2, true);
                }
                tx.flags = TLObject.setFlag(tx.flags, TLObject.FLAG_10, tx.gift);
                if (TYPE_TOPUP.equals(type)) {
                    tx.peer = makeInAppPeer();
                } else {
                    final long peerDialogId = o.optLong("peerDialogId", 0);
                    tx.peer = makeTxPeer(peerDialogId);
                }
                list.add(tx);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static JSONArray loadJsonArray(int currentAccount, boolean ton) {
        try {
            final String raw = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS, 0)
                    .getString(key(currentAccount, ton), "[]");
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void saveJsonArray(int currentAccount, boolean ton, JSONArray arr) {
        ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, 0)
                .edit()
                .putString(key(currentAccount, ton), arr.toString())
                .apply();
    }

    private static String key(int account, boolean ton) {
        return "tx_" + account + "_" + (ton ? "ton" : "stars");
    }

    public static String buildSpendDescription(
            int currentAccount,
            long recipientDialogId,
            String userMessage,
            boolean anonymous) {
        final String name = DialogObject.getName(currentAccount, recipientDialogId);
        final String target = name != null && !name.isEmpty() ? name : String.valueOf(recipientDialogId);
        if (anonymous) {
            return "Gift · " + target;
        }
        if (userMessage != null && !userMessage.isEmpty()) {
            return "Gift · " + target + " · " + userMessage;
        }
        return "Gift · " + target;
    }
}

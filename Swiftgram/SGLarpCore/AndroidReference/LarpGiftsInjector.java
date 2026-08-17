package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/**
 * [MG] Re-apply stored LARP gifts after profile gifts list reloads from server.
 */
public final class LarpGiftsInjector {

    private LarpGiftsInjector() {
    }

    public static void maybeInject(int currentAccount, long dialogId, StarsController.GiftsList list, boolean firstPage) {
        if (!VisualGiftsHelper.isVisualGiftsEnabled() || list == null || !firstPage) {
            return;
        }
        final ArrayList<LarpGiftStore.Entry> entries = new ArrayList<>(
                LarpGiftStore.forOwner(currentAccount, dialogId));
        if (entries.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (LarpGiftStore.Entry entry : entries) {
            TL_stars.SavedStarGift existing = null;
            for (TL_stars.SavedStarGift g : list.gifts) {
                if (g == null) {
                    continue;
                }
                if (g.saved_id == entry.savedId) {
                    existing = g;
                    break;
                }
                if (entry.msgId > 0 && g.msg_id == entry.msgId) {
                    existing = g;
                    break;
                }
                if (g.gift != null && !TextUtils.isEmpty(entry.slug)
                        && TextUtils.equals(entry.slug, g.gift.slug)) {
                    existing = g;
                    break;
                }
            }
            if (existing != null) {
                if (existing.unsaved != entry.unsaved) {
                    existing.unsaved = entry.unsaved;
                    changed = true;
                }
                continue;
            }
            final TL_stars.SavedStarGift saved = buildSavedGift(currentAccount, entry);
            if (saved != null) {
                list.gifts.add(0, saved);
                changed = true;
            }
        }
        if (changed) {
            list.totalCount = Math.max(list.totalCount, list.gifts.size());
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.starUserGiftsLoaded, dialogId, list);
        }
    }

    private static boolean findInList(StarsController.GiftsList list, LarpGiftStore.Entry entry) {
        for (TL_stars.SavedStarGift g : list.gifts) {
            if (g == null) {
                continue;
            }
            if (g.saved_id == entry.savedId) {
                return true;
            }
            if (entry.msgId > 0 && g.msg_id == entry.msgId) {
                return true;
            }
            if (g.gift != null && !TextUtils.isEmpty(entry.slug)
                    && TextUtils.equals(entry.slug, g.gift.slug)) {
                return true;
            }
        }
        return false;
    }

    private static TL_stars.SavedStarGift buildSavedGift(int currentAccount, LarpGiftStore.Entry entry) {
        TL_stars.StarGift gift = findGift(currentAccount, entry);
        if (gift == null) {
            return null;
        }
        final long fromId = entry.senderId != 0
                ? entry.senderId
                : UserConfig.getInstance(currentAccount).getClientUserId();
        final TLRPC.Peer fromPeer = MessagesController.getInstance(currentAccount).getPeer(fromId);

        final TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
        saved.flags = TLObject.setFlag(0, 2, true);
        saved.from_id = fromPeer;
        saved.date = entry.date > 0 ? entry.date : ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        saved.gift = gift;
        saved.unsaved = entry.unsaved;
        saved.saved_id = entry.savedId;
        saved.flags = TLObject.setFlag(saved.flags, TLObject.FLAG_11, true);
        if (gift instanceof TL_stars.TL_starGiftUnique) {
            saved.transfer_stars = 25;
            saved.flags = TLObject.setFlag(saved.flags, TLObject.FLAG_8, true);
        }
        if (entry.msgId > 0 && entry.ownerDialogId > 0) {
            saved.msg_id = entry.msgId;
            saved.flags = TLObject.setFlag(saved.flags, 8, true);
        }
        if (entry.resaleStars > 0 && gift instanceof TL_stars.TL_starGiftUnique) {
            final TL_stars.TL_starGiftUnique unique = (TL_stars.TL_starGiftUnique) gift;
            unique.flags = TLObject.setFlag(unique.flags, 16, true);
            unique.resale_ton_only = entry.resaleTonOnly;
            unique.resell_amount = new ArrayList<>();
            unique.resell_amount.add(TL_stars.StarsAmount.ofStars(entry.resaleStars));
        }
        return saved;
    }

    public static void syncToProfileList(int currentAccount, long dialogId) {
        final StarsController.GiftsList list = StarsController.getInstance(currentAccount)
                .getProfileGiftsList(dialogId, false);
        if (list != null) {
            maybeInject(currentAccount, dialogId, list, true);
        }
    }

    public static void syncAllForAccount(int currentAccount) {
        if (!VisualGiftsHelper.isVisualGiftsEnabled()) {
            return;
        }
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (selfId != 0) {
            syncToProfileList(currentAccount, selfId);
        }
        for (LarpGiftStore.Entry entry : LarpGiftStore.forOwner(currentAccount, selfId)) {
            if (entry.ownerDialogId != 0 && entry.ownerDialogId != selfId) {
                syncToProfileList(currentAccount, entry.ownerDialogId);
            }
        }
    }

    private static TL_stars.StarGift findGift(int currentAccount, LarpGiftStore.Entry entry) {
        final TL_stars.StarGift rebuilt = LarpGiftStore.rebuildGift(currentAccount, entry);
        if (rebuilt != null) {
            return rebuilt;
        }
        final TL_stars.StarGift cached = LarpGiftStore.getCachedGift(
                currentAccount, entry.ownerDialogId, entry.savedId);
        if (cached != null) {
            return cached;
        }
        final StarsController stars = StarsController.getInstance(currentAccount);
        final StarsController.GiftsList giftsList = stars.getProfileGiftsList(entry.ownerDialogId, false);
        if (!TextUtils.isEmpty(entry.slug) && giftsList != null) {
            for (TL_stars.SavedStarGift g : giftsList.gifts) {
                if (g != null && g.gift != null && TextUtils.equals(entry.slug, g.gift.slug)) {
                    return g.gift;
                }
            }
        }
        for (TL_stars.StarGift g : stars.gifts) {
            if (g != null && g.id == entry.giftId) {
                return g;
            }
        }
        return null;
    }
}

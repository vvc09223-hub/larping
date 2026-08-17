package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/**
 * [MG] Local-only transfer / hide / marketplace for LARP gifts.
 */
public final class LarpGiftActionsHelper {

    private LarpGiftActionsHelper() {
    }

    public static long resolveOwnerDialogId(int account, long hintDialogId, TL_stars.SavedStarGift gift) {
        if (gift == null) {
            return hintDialogId;
        }
        if (LarpGiftStore.findBySavedGift(account, hintDialogId, gift) != null) {
            return hintDialogId;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId != 0 && LarpGiftStore.findBySavedGift(account, selfId, gift) != null) {
            return selfId;
        }
        if (gift.gift != null && gift.gift.owner_id != null) {
            final long ownerId = DialogObject.getPeerDialogId(gift.gift.owner_id);
            if (ownerId != 0 && LarpGiftStore.findBySavedGift(account, ownerId, gift) != null) {
                return ownerId;
            }
            if (ownerId != 0) {
                return ownerId;
            }
        }
        return hintDialogId != 0 ? hintDialogId : selfId;
    }

    public static boolean isLarpGift(int account, long ownerDialogId, TL_stars.SavedStarGift gift) {
        return shouldHandleLocally(account, ownerDialogId, gift);
    }

    public static boolean shouldHandleLocally(int account, long ownerDialogId, TL_stars.SavedStarGift gift) {
        if (!VisualGiftsHelper.isVisualGiftsEnabled() || gift == null || gift.gift == null) {
            return false;
        }
        if (LarpGiftStore.findBySavedGift(account, ownerDialogId, gift) != null) {
            return true;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId != 0 && LarpGiftStore.findBySavedGift(account, selfId, gift) != null) {
            return true;
        }
        if (!TextUtils.isEmpty(gift.gift.slug)) {
            return true;
        }
        if ((gift.flags & 8) != 0 && gift.msg_id != 0) {
            return true;
        }
        if (gift.saved_id != 0 && (gift.flags & TLObject.FLAG_11) != 0) {
            return true;
        }
        return false;
    }

    private static void ensureRegistered(int account, long ownerDialogId, TL_stars.SavedStarGift savedGift) {
        if (savedGift == null || savedGift.gift == null || savedGift.saved_id == 0) {
            return;
        }
        if (LarpGiftStore.findBySavedGift(account, ownerDialogId, savedGift) != null) {
            return;
        }
        LarpGiftStore.register(
                account,
                ownerDialogId,
                savedGift.saved_id,
                savedGift.msg_id,
                savedGift.gift,
                savedGift.from_id,
                savedGift.unsaved);
    }

    public static TL_stars.InputSavedStarGift getInputStarGift(int account, long ownerDialogId, TL_stars.SavedStarGift gift) {
        return LarpGiftStore.getInput(account, ownerDialogId, gift);
    }

    public static boolean transferGift(
            int account,
            long ownerDialogId,
            TL_stars.SavedStarGift savedGift,
            long toDialogId,
            Utilities.Callback<TLRPC.TL_error> whenDone) {
        final long resolvedOwner = resolveOwnerDialogId(account, ownerDialogId, savedGift);
        if (!shouldHandleLocally(account, resolvedOwner, savedGift) || savedGift == null || savedGift.gift == null) {
            return false;
        }
        ensureRegistered(account, resolvedOwner, savedGift);

        final long transferStars = savedGift.transfer_stars > 0 ? savedGift.transfer_stars : 25;
        if (savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
            if (!LarpBalanceHelper.spendStars(account, transferStars)) {
                final TLRPC.TL_error err = new TLRPC.TL_error();
                err.text = "BALANCE_TOO_LOW";
                AndroidUtilities.runOnUIThread(() -> {
                    if (whenDone != null) {
                        whenDone.run(err);
                    }
                });
                return true;
            }
        }

        LarpGiftStore.remove(account, resolvedOwner, savedGift.saved_id);

        final StarsController.GiftsList list = StarsController.getInstance(account)
                .getProfileGiftsList(resolvedOwner, false);
        if (list != null) {
            list.gifts.remove(savedGift);
            list.totalCount = Math.max(0, list.totalCount - 1);
            NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.starUserGiftsLoaded, resolvedOwner, list);
        }

        final String message = "";
        if (savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
            VisualGiftsHelper.deliverUniqueGiftToPeer(
                    account, toDialogId, (TL_stars.TL_starGiftUnique) savedGift.gift, message, true);
        } else {
            VisualGiftsHelper.deliverPurchasedGift(
                    account, savedGift.gift, toDialogId, message, false);
        }

        GiftRelayManager.pushGift(
                account,
                toDialogId > 0 ? toDialogId : -toDialogId,
                savedGift.gift,
                savedGift.gift instanceof TL_stars.TL_starGiftUnique,
                false,
                0,
                message,
                false);

        AndroidUtilities.runOnUIThread(() -> {
            if (whenDone != null) {
                whenDone.run(null);
            }
        });
        return true;
    }

    public static boolean toggleProfileVisible(
            int account,
            long ownerDialogId,
            TL_stars.SavedStarGift savedGift,
            boolean showOnProfile) {
        final long resolvedOwner = resolveOwnerDialogId(account, ownerDialogId, savedGift);
        if (!shouldHandleLocally(account, resolvedOwner, savedGift) || savedGift == null) {
            return false;
        }
        ensureRegistered(account, resolvedOwner, savedGift);
        savedGift.unsaved = !showOnProfile;
        LarpGiftStore.updateUnsaved(account, resolvedOwner, savedGift.saved_id, savedGift.unsaved);
        final StarsController.GiftsCollections collections = StarsController.getInstance(account)
                .getProfileGiftCollectionsList(resolvedOwner, false);
        if (collections != null) {
            collections.updateGiftsUnsaved(savedGift, savedGift.unsaved);
        }
        final StarsController.GiftsList list = StarsController.getInstance(account)
                .getProfileGiftsList(resolvedOwner, false);
        if (list != null) {
            for (TL_stars.SavedStarGift g : list.gifts) {
                if (g != null && g.saved_id == savedGift.saved_id) {
                    g.unsaved = savedGift.unsaved;
                    break;
                }
            }
            NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.starUserGiftsLoaded, resolvedOwner, list);
        }
        return true;
    }

    public static boolean updateResellPrice(
            int account,
            long ownerDialogId,
            TL_stars.SavedStarGift savedGift,
            TL_stars.TL_starGiftUnique gift,
            AmountUtils.Amount price,
            Runnable onDone) {
        final long resolvedOwner = resolveOwnerDialogId(account, ownerDialogId, savedGift);
        if (!shouldHandleLocally(account, resolvedOwner, savedGift) || gift == null) {
            return false;
        }
        ensureRegistered(account, resolvedOwner, savedGift);
        final int stars = (int) Math.max(0, price.asDecimal());
        gift.flags = org.telegram.tgnet.TLObject.setFlag(gift.flags, 16, stars > 0);
        gift.resale_ton_only = price.currency == AmountUtils.Currency.TON;
        gift.resell_amount = new ArrayList<>();
        if (stars > 0) {
            gift.resell_amount.add(price.convertTo(AmountUtils.Currency.STARS).toTl());
            gift.resell_amount.add(price.convertTo(AmountUtils.Currency.TON).toTl());
        }
        LarpGiftStore.updateResale(account, resolvedOwner, savedGift.saved_id, stars, gift.resale_ton_only);
        AndroidUtilities.runOnUIThread(() -> {
            if (onDone != null) {
                onDone.run();
            }
        });
        return true;
    }

    public static boolean removeFromMarket(
            int account,
            long ownerDialogId,
            TL_stars.SavedStarGift savedGift,
            TL_stars.TL_starGiftUnique gift,
            Runnable onDone) {
        return updateResellPrice(
                account,
                ownerDialogId,
                savedGift,
                gift,
                AmountUtils.Amount.fromNano(0, AmountUtils.Currency.STARS),
                onDone);
    }
}

package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * [MG] Persistent registry for local LARP gifts (survives profile gifts reload).
 */
public final class LarpGiftStore {

    private static final String PREFS = "mg_larp_gifts";
    private static final String KEY_ENTRIES = "entries";
    private static final HashMap<String, TL_stars.StarGift> GIFT_CACHE = new HashMap<>();

    private static String cacheKey(int account, long ownerDialogId, long savedId) {
        return account + ":" + ownerDialogId + ":" + savedId;
    }

    public static final class Entry {
        public int account;
        public long ownerDialogId;
        public long savedId;
        public int msgId;
        public long giftId;
        public String slug = "";
        public boolean unique;
        public boolean unsaved;
        public int resaleStars;
        public boolean resaleTonOnly;
        public int date;
        public long senderId;
        public String senderName = "";
        public String giftTitle = "";
        public long giftStars;
    }

    private LarpGiftStore() {
    }

    public static void register(
            int account,
            long ownerDialogId,
            long savedId,
            int msgId,
            TL_stars.StarGift gift,
            TLRPC.Peer fromPeer,
            boolean unsaved) {
        if (!VisualGiftsHelper.isVisualGiftsEnabled() || gift == null || savedId == 0) {
            return;
        }
        final Entry entry = new Entry();
        entry.account = account;
        entry.ownerDialogId = ownerDialogId;
        entry.savedId = savedId;
        entry.msgId = msgId;
        entry.giftId = gift.id;
        entry.slug = gift.slug != null ? gift.slug : "";
        entry.giftTitle = gift.title != null ? gift.title : "";
        entry.giftStars = gift.stars;
        entry.unique = gift instanceof TL_stars.TL_starGiftUnique;
        if (!entry.unique) {
            entry.slug = "";
        }
        entry.unsaved = unsaved;
        entry.date = (int) (System.currentTimeMillis() / 1000L);
        if (fromPeer != null) {
            if (fromPeer.user_id != 0) {
                entry.senderId = fromPeer.user_id;
            } else if (fromPeer.channel_id != 0) {
                entry.senderId = -fromPeer.channel_id;
            } else if (fromPeer.chat_id != 0) {
                entry.senderId = -fromPeer.chat_id;
            }
        }
        final List<Entry> all = loadAll();
        final Iterator<Entry> it = all.iterator();
        while (it.hasNext()) {
            final Entry e = it.next();
            if (e.account == account && e.ownerDialogId == ownerDialogId && e.savedId == savedId) {
                it.remove();
                break;
            }
        }
        all.add(0, entry);
        saveAll(all);
        GIFT_CACHE.put(cacheKey(account, ownerDialogId, savedId), gift);
        publishInventoryToServer(account);
    }

    public static TL_stars.StarGift rebuildGift(int account, Entry entry) {
        if (entry == null) {
            return null;
        }
        final TL_stars.StarGift cached = getCachedGift(account, entry.ownerDialogId, entry.savedId);
        if (cached != null) {
            return cached;
        }
        final StarsController stars = StarsController.getInstance(account);
        if (entry.giftId != 0) {
            for (TL_stars.StarGift g : stars.gifts) {
                if (g != null && g.id == entry.giftId) {
                    GIFT_CACHE.put(cacheKey(account, entry.ownerDialogId, entry.savedId), g);
                    return g;
                }
            }
        }
        if (!TextUtils.isEmpty(entry.slug)) {
            final StarsController.GiftsList list = stars.getProfileGiftsList(entry.ownerDialogId, false);
            if (list != null) {
                for (TL_stars.SavedStarGift g : list.gifts) {
                    if (g != null && g.gift != null && TextUtils.equals(entry.slug, g.gift.slug)) {
                        GIFT_CACHE.put(cacheKey(account, entry.ownerDialogId, entry.savedId), g.gift);
                        return g.gift;
                    }
                }
            }
        }
        if (entry.unique && entry.resaleStars > 0 && !TextUtils.isEmpty(entry.slug)) {
            final TL_stars.TL_starGiftUnique unique = new TL_stars.TL_starGiftUnique();
            unique.id = entry.giftId;
            unique.gift_id = entry.giftId;
            unique.slug = entry.slug;
            unique.title = entry.giftTitle;
            unique.stars = entry.giftStars > 0 ? entry.giftStars : 15;
            unique.sticker = borrowSticker(stars.gifts);
            return unique.sticker != null ? unique : null;
        }
        if (entry.giftId == 0 && TextUtils.isEmpty(entry.giftTitle)) {
            return null;
        }
        final TL_stars.TL_starGift gift = new TL_stars.TL_starGift();
        gift.id = entry.giftId;
        gift.stars = entry.giftStars > 0 ? entry.giftStars : 15;
        if (!TextUtils.isEmpty(entry.slug)) {
            gift.slug = entry.slug;
        }
        if (!TextUtils.isEmpty(entry.giftTitle)) {
            gift.title = entry.giftTitle;
        }
        gift.sticker = borrowSticker(stars.gifts);
        return gift.sticker != null ? gift : null;
    }

    private static TLRPC.Document borrowSticker(ArrayList<TL_stars.StarGift> gifts) {
        if (gifts == null) {
            return null;
        }
        for (TL_stars.StarGift g : gifts) {
            if (g != null && g.sticker != null) {
                return g.sticker;
            }
        }
        return null;
    }

    public static TL_stars.StarGift getCachedGift(int account, long ownerDialogId, long savedId) {
        return GIFT_CACHE.get(cacheKey(account, ownerDialogId, savedId));
    }

    public static Entry findBySavedGift(int account, long ownerDialogId, TL_stars.SavedStarGift gift) {
        if (gift == null) {
            return null;
        }
        for (Entry e : loadAll()) {
            if (e.account != account || e.ownerDialogId != ownerDialogId) {
                continue;
            }
            if (gift.saved_id != 0 && e.savedId == gift.saved_id) {
                return e;
            }
            if (gift.msg_id > 0 && e.msgId == gift.msg_id) {
                return e;
            }
            if (gift.gift != null && !TextUtils.isEmpty(gift.gift.slug)
                    && TextUtils.equals(gift.gift.slug, e.slug)) {
                return e;
            }
        }
        return null;
    }

    public static boolean isLarpGift(int account, long ownerDialogId, TL_stars.SavedStarGift gift) {
        return findBySavedGift(account, ownerDialogId, gift) != null;
    }

    public static void updateUnsaved(int account, long ownerDialogId, long savedId, boolean unsaved) {
        final List<Entry> all = loadAll();
        for (Entry e : all) {
            if (e.account == account && e.ownerDialogId == ownerDialogId && e.savedId == savedId) {
                e.unsaved = unsaved;
                saveAll(all);
                publishInventoryToServer(account);
                return;
            }
        }
    }

    public static void updateResale(int account, long ownerDialogId, long savedId, int stars, boolean tonOnly) {
        final List<Entry> all = loadAll();
        for (Entry e : all) {
            if (e.account == account && e.ownerDialogId == ownerDialogId && e.savedId == savedId) {
                e.resaleStars = stars;
                e.resaleTonOnly = tonOnly;
                saveAll(all);
                return;
            }
        }
    }

    public static void remove(int account, long ownerDialogId, long savedId) {
        final List<Entry> all = loadAll();
        all.removeIf(e -> e.account == account
                && e.ownerDialogId == ownerDialogId
                && e.savedId == savedId);
        saveAll(all);
        GIFT_CACHE.remove(cacheKey(account, ownerDialogId, savedId));
        publishInventoryToServer(account);
    }

    public static List<Entry> forOwner(int account, long ownerDialogId) {
        final List<Entry> result = new ArrayList<>();
        final long selfId = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
        for (Entry e : loadAll()) {
            if (e.account != account) {
                continue;
            }
            if (e.ownerDialogId == ownerDialogId) {
                result.add(e);
                continue;
            }
            if (selfId != 0 && ownerDialogId == selfId && e.ownerDialogId == 0) {
                result.add(e);
            } else if (selfId != 0 && ownerDialogId == 0 && e.ownerDialogId == selfId) {
                result.add(e);
            }
        }
        return result;
    }

    public static TL_stars.InputSavedStarGift getInput(int account, long ownerDialogId, TL_stars.SavedStarGift gift) {
        if (!VisualGiftsHelper.isVisualGiftsEnabled() || gift == null || gift.gift == null) {
            return null;
        }
        if (!TextUtils.isEmpty(gift.gift.slug)) {
            final TL_stars.TL_inputSavedStarGiftSlug input = new TL_stars.TL_inputSavedStarGiftSlug();
            input.slug = gift.gift.slug;
            return input;
        }
        if (gift.msg_id != 0) {
            final TL_stars.TL_inputSavedStarGiftUser input = new TL_stars.TL_inputSavedStarGiftUser();
            input.msg_id = gift.msg_id;
            return input;
        }
        // Never send local fake saved_id to Telegram — it returns SAVED_ID_EMPTY.
        return null;
    }

    private static List<Entry> loadAll() {
        final List<Entry> list = new ArrayList<>();
        try {
            final String raw = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS, 0)
                    .getString(KEY_ENTRIES, "[]");
            final JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                final Entry e = new Entry();
                e.account = o.optInt("account", 0);
                e.ownerDialogId = o.optLong("owner", 0);
                e.savedId = o.optLong("saved_id", 0);
                e.msgId = o.optInt("msg_id", 0);
                e.giftId = o.optLong("gift_id", 0);
                e.slug = o.optString("slug", "");
                e.unique = o.optBoolean("unique", false);
                if (e.unique && o.optLong("resale_stars", 0) <= 0) {
                    e.unique = false;
                    e.slug = "";
                }
                e.unsaved = o.optBoolean("unsaved", false);
                e.resaleStars = o.optInt("resale_stars", 0);
                e.resaleTonOnly = o.optBoolean("resale_ton", false);
                e.date = o.optInt("date", 0);
                e.senderId = o.optLong("sender_id", 0);
                e.senderName = o.optString("sender_name", "");
                e.giftTitle = o.optString("gift_title", "");
                e.giftStars = o.optLong("gift_stars", 0);
                if (e.savedId != 0) {
                    list.add(e);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void saveAll(List<Entry> list) {
        try {
            final JSONArray arr = new JSONArray();
            int count = 0;
            for (Entry e : list) {
                if (count++ > 500) {
                    break;
                }
                final JSONObject o = new JSONObject();
                o.put("account", e.account);
                o.put("owner", e.ownerDialogId);
                o.put("saved_id", e.savedId);
                o.put("msg_id", e.msgId);
                o.put("gift_id", e.giftId);
                o.put("slug", e.slug);
                o.put("unique", e.unique);
                o.put("unsaved", e.unsaved);
                o.put("resale_stars", e.resaleStars);
                o.put("resale_ton", e.resaleTonOnly);
                o.put("date", e.date);
                o.put("sender_id", e.senderId);
                o.put("sender_name", e.senderName);
                o.put("gift_title", e.giftTitle);
                o.put("gift_stars", e.giftStars);
                arr.put(o);
            }
            ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS, 0)
                    .edit()
                    .putString(KEY_ENTRIES, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static void publishInventoryToServer(int account) {
        if (!LicenseManager.isActive() || !VisualGiftsHelper.isVisualGiftsEnabled()) {
            return;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId == 0) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final JSONArray items = new JSONArray();
                for (Entry e : forOwner(account, selfId)) {
                    final JSONObject o = new JSONObject();
                    o.put("saved_id", String.valueOf(e.savedId));
                    o.put("gift_id", String.valueOf(e.giftId));
                    o.put("slug", e.slug);
                    o.put("title", e.giftTitle);
                    o.put("stars", e.giftStars);
                    o.put("unique", e.unique);
                    o.put("unsaved", e.unsaved);
                    o.put("msg_id", e.msgId);
                    o.put("date", e.date);
                    o.put("sender_id", e.senderId);
                    items.put(o);
                }
                final JSONObject body = new JSONObject();
                body.put("key", SharedConfig.larpLicenseKey);
                body.put("hwid", LicenseManager.getHardwareId());
                body.put("user_id", selfId);
                body.put("gifts", items);
                LicenseManager.postJson("/api/gift/inventory/publish", body, null);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static void fetchInventoryFromServer(int account) {
        if (!LicenseManager.isActive() || !VisualGiftsHelper.isVisualGiftsEnabled()) {
            return;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId == 0) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final JSONObject body = new JSONObject();
                body.put("key", SharedConfig.larpLicenseKey);
                body.put("hwid", LicenseManager.getHardwareId());
                body.put("user_id", selfId);
                final JSONObject response = LicenseManager.postJsonSync("/api/gift/inventory/get", body);
                if (response == null || !response.optBoolean("ok", false)) {
                    return;
                }
                final JSONArray gifts = response.optJSONArray("gifts");
                if (gifts == null || gifts.length() == 0) {
                    return;
                }
                final List<Entry> all = loadAll();
                boolean changed = false;
                for (int i = 0; i < gifts.length(); i++) {
                    final JSONObject o = gifts.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    long savedId = parseLong(o.optString("saved_id", "0"));
                    if (savedId == 0) {
                        savedId = o.optLong("saved_id", 0);
                    }
                    if (savedId == 0) {
                        continue;
                    }
                    Entry existing = null;
                    for (Entry e : all) {
                        if (e.account == account && e.ownerDialogId == selfId && e.savedId == savedId) {
                            existing = e;
                            break;
                        }
                    }
                    if (existing == null) {
                        existing = new Entry();
                        existing.account = account;
                        existing.ownerDialogId = selfId;
                        existing.savedId = savedId;
                        all.add(0, existing);
                        changed = true;
                    }
                    final long giftId = parseLong(o.optString("gift_id", "0"));
                    if (giftId != 0) {
                        existing.giftId = giftId;
                    }
                    existing.slug = o.optString("slug", existing.slug);
                    existing.giftTitle = o.optString("title", existing.giftTitle);
                    existing.giftStars = o.optLong("stars", existing.giftStars);
                    existing.unique = o.optBoolean("unique", existing.unique);
                    existing.unsaved = o.optBoolean("unsaved", existing.unsaved);
                    existing.msgId = o.optInt("msg_id", existing.msgId);
                    existing.date = o.optInt("date", existing.date);
                    existing.senderId = o.optLong("sender_id", existing.senderId);
                    changed = true;
                }
                if (changed) {
                    saveAll(all);
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(
                            () -> LarpGiftsInjector.syncAllForAccount(account));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return 0;
        }
    }
}

package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Relay channel LARP overlays (admins + fake posts) across MercuryGram / LarpGram clients.
 */
public final class LarpChannelRelay {

    public static final class FakePost {
        public long id;
        public long fromUserId;
        public String text = "";
        public int date;
    }

    public static final class RemoteChannel {
        public long chatId;
        public String title = "";
        public String username = "";
        public int participantsCount;
        public ArrayList<Long> adminIds = new ArrayList<>();
        public ArrayList<FakePost> posts = new ArrayList<>();
        public long updatedAt;
    }

    private static final ConcurrentHashMap<Long, RemoteChannel> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger LOCAL_MSG_SEQ = new AtomicInteger((int) (System.currentTimeMillis() % 100000));

    private LarpChannelRelay() {
    }

    public static ArrayList<Long> remoteAdminIds(long chatId) {
        RemoteChannel r = CACHE.get(chatId);
        return r != null ? new ArrayList<>(r.adminIds) : new ArrayList<>();
    }

    public static RemoteChannel getCached(long chatId) {
        return CACHE.get(chatId);
    }

    public static void publish(int account, LarpChannelAdminOverlay.Overlay overlay) {
        if (!LicenseManager.isActive() || !SharedConfig.larpVisualMenu || overlay == null || overlay.chatId == 0) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("key", SharedConfig.larpLicenseKey);
                body.put("hwid", LicenseManager.getHardwareId());
                body.put("chat_id", overlay.chatId);
                body.put("owner_user_id", UserConfig.getInstance(account).getClientUserId());
                body.put("title", overlay.title != null ? overlay.title : "");
                body.put("username", overlay.username != null ? overlay.username : "");
                body.put("participants_count", overlay.participantsCount);
                body.put("enabled", overlay.enabled);
                JSONArray admins = new JSONArray();
                long selfId = UserConfig.getInstance(account).getClientUserId();
                admins.put(selfId);
                for (String token : LarpChannelAdminOverlay.fakeAdminTokens(overlay.chatId)) {
                    try {
                        long id = Long.parseLong(token);
                        if (id != selfId) {
                            admins.put(id);
                        }
                    } catch (Exception ignored) {
                    }
                }
                body.put("admins", admins);
                JSONArray posts = new JSONArray();
                if (overlay.posts != null) {
                    for (FakePost p : overlay.posts) {
                        JSONObject jp = new JSONObject();
                        jp.put("id", p.id);
                        jp.put("from_user_id", p.fromUserId);
                        jp.put("text", p.text != null ? p.text : "");
                        jp.put("date", p.date);
                        posts.put(jp);
                    }
                }
                body.put("posts", posts);
                LicenseManager.postJson("/api/channel/publish", body, null);
            } catch (Exception ignored) {
            }
        });
    }

    /** Inject locally stored LARP posts into the open chat (same device). */
    public static void injectLocalPosts(int account, long chatId) {
        if (chatId <= 0 || !LarpChannelAdminOverlay.isEnabled(chatId)) {
            return;
        }
        LarpChannelAdminOverlay.Overlay o = LarpChannelAdminOverlay.get(chatId);
        if (o == null || o.posts == null) {
            return;
        }
        long selfId = UserConfig.getInstance(account).getClientUserId();
        for (FakePost p : o.posts) {
            injectSinglePost(account, chatId, p, p.fromUserId == selfId);
        }
    }

    public static void fetch(int account, long chatId) {
        if (!LicenseManager.isActive() || chatId <= 0) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> injectLocalPosts(account, chatId));
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("key", SharedConfig.larpLicenseKey);
                body.put("hwid", LicenseManager.getHardwareId());
                body.put("chat_id", chatId);
                JSONObject response = LicenseManager.postJsonSync("/api/channel/get", body);
                if (response == null || !response.optBoolean("ok", false)) {
                    return;
                }
                JSONObject channel = response.optJSONObject("channel");
                if (channel == null || channel.length() == 0) {
                    return;
                }
                RemoteChannel remote = new RemoteChannel();
                remote.chatId = chatId;
                remote.title = channel.optString("title", "");
                remote.username = channel.optString("username", "");
                remote.participantsCount = channel.optInt("participants_count", 0);
                remote.updatedAt = channel.optLong("updated_at", 0);
                JSONArray admins = channel.optJSONArray("admins");
                if (admins != null) {
                    for (int i = 0; i < admins.length(); i++) {
                        long id = admins.optLong(i, 0);
                        if (id != 0) {
                            remote.adminIds.add(id);
                        }
                    }
                }
                JSONArray posts = channel.optJSONArray("posts");
                if (posts != null) {
                    for (int i = 0; i < posts.length(); i++) {
                        JSONObject jp = posts.optJSONObject(i);
                        if (jp == null) {
                            continue;
                        }
                        FakePost p = new FakePost();
                        p.id = jp.optLong("id", 0);
                        p.fromUserId = jp.optLong("from_user_id", 0);
                        p.text = jp.optString("text", "");
                        p.date = jp.optInt("date", 0);
                        if (!TextUtils.isEmpty(p.text)) {
                            remote.posts.add(p);
                        }
                    }
                }
                CACHE.put(chatId, remote);
                AndroidUtilities.runOnUIThread(() -> {
                    LarpChannelAdminOverlay.mergeRemote(account, remote);
                    injectRemotePosts(account, remote);
                    NotificationCenter.getInstance(account).postNotificationName(
                            NotificationCenter.updateInterfaces,
                            MessagesController.UPDATE_MASK_CHAT_MEMBERS | MessagesController.UPDATE_MASK_CHAT);
                });
            } catch (Exception ignored) {
            }
        });
    }

    public static boolean trySendLocalPost(int account, long dialogId, String text) {
        if (TextUtils.isEmpty(text) || dialogId >= 0) {
            return false;
        }
        long chatId = -dialogId;
        if (!LarpChannelAdminOverlay.isEnabled(chatId)) {
            return false;
        }
        long selfId = UserConfig.getInstance(account).getClientUserId();
        FakePost post = new FakePost();
        post.id = -(System.currentTimeMillis() % Integer.MAX_VALUE) - LOCAL_MSG_SEQ.incrementAndGet();
        post.fromUserId = selfId;
        post.text = text;
        post.date = (int) (System.currentTimeMillis() / 1000L);
        LarpChannelAdminOverlay.addPost(chatId, post);
        LarpChannelAdminOverlay.Overlay o = LarpChannelAdminOverlay.get(chatId);
        if (o != null) {
            publish(account, o);
        }
        injectSinglePost(account, chatId, post, true);
        return true;
    }

    private static void injectRemotePosts(int account, RemoteChannel remote) {
        if (remote == null || remote.posts == null) {
            return;
        }
        for (FakePost p : remote.posts) {
            injectSinglePost(account, remote.chatId, p,
                    p.fromUserId == UserConfig.getInstance(account).getClientUserId());
        }
    }

    private static void injectSinglePost(int account, long chatId, FakePost post, boolean out) {
        if (post == null || TextUtils.isEmpty(post.text)) {
            return;
        }
        try {
            final long dialogId = -chatId;
            TLRPC.TL_message msg = new TLRPC.TL_message();
            int mid = (int) post.id;
            if (mid >= 0) {
                mid = -Math.abs(mid == 0 ? LOCAL_MSG_SEQ.incrementAndGet() : mid);
            }
            // Keep local fake ids in a stable negative range
            if (mid > -1_000_000) {
                mid = -1_000_000 - Math.abs(mid);
            }
            msg.id = mid;
            msg.message = post.text;
            msg.date = post.date > 0 ? post.date : (int) (System.currentTimeMillis() / 1000L);
            msg.out = out;
            msg.unread = false;
            msg.from_id = new TLRPC.TL_peerUser();
            msg.from_id.user_id = post.fromUserId != 0
                    ? post.fromUserId
                    : UserConfig.getInstance(account).getClientUserId();
            msg.peer_id = new TLRPC.TL_peerChannel();
            msg.peer_id.channel_id = chatId;
            msg.post = true;
            msg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
            msg.views = 1;
            msg.flags |= 1024; // views

            final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();
            messagesArr.add(msg);
            final MessageObject mo = new MessageObject(account, msg, true, true);
            mo.setType();
            final ArrayList<MessageObject> pushMessages = new ArrayList<>();
            pushMessages.add(mo);

            AndroidUtilities.runOnUIThread(() -> {
                try {
                    MessagesController.getInstance(account)
                            .updateInterfaceWithMessages(dialogId, pushMessages, 0);
                    NotificationCenter.getInstance(account).postNotificationName(
                            NotificationCenter.didReceiveNewMessages,
                            dialogId,
                            pushMessages,
                            false,
                            0);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}

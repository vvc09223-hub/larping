package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [MG] Local-only channel admin overlay (visual). No Telegram API mutations.
 */
public final class LarpChannelAdminOverlay {

    public static final class Overlay {
        public long chatId;
        public String link = "";
        public boolean enabled = true;
        public boolean forceCreator = true;
        public String title = "";
        public String username = "";
        public String photoPath = "";
        public int participantsCount; // 0 = use real
        public String fakeAdmins = ""; // comma-separated @user or ids
        public ArrayList<LarpChannelRelay.FakePost> posts = new ArrayList<>();

        public Overlay copy() {
            Overlay o = new Overlay();
            o.chatId = chatId;
            o.link = link;
            o.enabled = enabled;
            o.forceCreator = forceCreator;
            o.title = title;
            o.username = username;
            o.photoPath = photoPath;
            o.participantsCount = participantsCount;
            o.fakeAdmins = fakeAdmins;
            o.posts = new ArrayList<>();
            if (posts != null) {
                for (LarpChannelRelay.FakePost p : posts) {
                    LarpChannelRelay.FakePost c = new LarpChannelRelay.FakePost();
                    c.id = p.id;
                    c.fromUserId = p.fromUserId;
                    c.text = p.text;
                    c.date = p.date;
                    o.posts.add(c);
                }
            }
            return o;
        }
    }

    public interface ResolveCallback {
        void onResult(boolean ok, Overlay overlay, String message);
    }

    private static final String PREFS = "mainconfig";
    private static final String KEY = "mg_larpChannelOverlays";
    private static final ConcurrentHashMap<Long, Overlay> CACHE = new ConcurrentHashMap<>();
    private static boolean loaded;

    private LarpChannelAdminOverlay() {
    }

    public static boolean gateOk() {
        return LicenseManager.isActive() && SharedConfig.larpVisualMenu;
    }

    public static boolean isEnabled(long chatId) {
        if (!gateOk() || chatId == 0) {
            return false;
        }
        ensureLoaded();
        Overlay o = CACHE.get(chatId);
        return o != null && o.enabled;
    }

    public static boolean isEnabled(TLRPC.Chat chat) {
        return chat != null && isEnabled(chat.id);
    }

    public static Overlay get(long chatId) {
        ensureLoaded();
        return CACHE.get(chatId);
    }

    public static ArrayList<Overlay> all() {
        ensureLoaded();
        return new ArrayList<>(CACHE.values());
    }

    public static void put(Overlay overlay) {
        if (overlay == null || overlay.chatId == 0) {
            return;
        }
        ensureLoaded();
        CACHE.put(overlay.chatId, overlay.copy());
        save();
        notifyChanged(overlay.chatId);
        LarpChannelRelay.publish(UserConfig.selectedAccount, overlay);
    }

    public static void addPost(long chatId, LarpChannelRelay.FakePost post) {
        if (chatId == 0 || post == null) {
            return;
        }
        ensureLoaded();
        Overlay existing = CACHE.get(chatId);
        if (existing == null) {
            return;
        }
        Overlay o = existing.copy();
        if (o.posts == null) {
            o.posts = new ArrayList<>();
        }
        o.posts.add(0, post);
        while (o.posts.size() > 100) {
            o.posts.remove(o.posts.size() - 1);
        }
        put(o);
    }

    public static void mergeRemote(int account, LarpChannelRelay.RemoteChannel remote) {
        if (remote == null || remote.chatId == 0) {
            return;
        }
        ensureLoaded();
        Overlay existing = CACHE.get(remote.chatId);
        // Always merge remote admins into local list for display / count
        if (existing != null && existing.enabled) {
            Overlay o = existing.copy();
            ArrayList<String> tokens = new ArrayList<>();
            if (!TextUtils.isEmpty(o.fakeAdmins)) {
                for (String t : o.fakeAdmins.split("[,;\\n]+")) {
                    String x = t == null ? "" : t.trim();
                    if (x.startsWith("@")) x = x.substring(1);
                    if (!x.isEmpty() && !tokens.contains(x)) tokens.add(x);
                }
            }
            for (Long id : remote.adminIds) {
                if (id == null || id == 0) continue;
                String s = String.valueOf(id);
                if (!tokens.contains(s)) {
                    tokens.add(s);
                }
            }
            o.fakeAdmins = TextUtils.join(", ", tokens);
            if (remote.participantsCount > 0 && o.participantsCount <= 0) {
                o.participantsCount = remote.participantsCount;
            }
            if (TextUtils.isEmpty(o.title) && !TextUtils.isEmpty(remote.title)) {
                o.title = remote.title;
            }
            CACHE.put(o.chatId, o);
            save();
            notifyChanged(o.chatId);
        } else if (existing == null && !remote.adminIds.isEmpty()) {
            // Soft enable for viewers who have license: create disabled-looking entry for merge only into remote cache
            // rights still require local enable OR being in adminIds (see canLarpManage)
        }
    }

    public static boolean canLarpManage(long chatId) {
        if (!gateOk() || chatId == 0) {
            return false;
        }
        if (isEnabled(chatId)) {
            return true;
        }
        long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        ArrayList<Long> remote = LarpChannelRelay.remoteAdminIds(chatId);
        return remote.contains(selfId);
    }

    public static void remove(long chatId) {
        ensureLoaded();
        CACHE.remove(chatId);
        save();
        notifyChanged(chatId);
    }

    public static void applyDisplay(TLRPC.Chat chat) {
        if (chat == null || !isEnabled(chat.id)) {
            return;
        }
        Overlay o = CACHE.get(chat.id);
        if (o == null) {
            return;
        }
        if (o.forceCreator) {
            chat.creator = true;
        }
        if (chat.admin_rights == null) {
            chat.admin_rights = fullAdminRights();
        } else {
            fillAdminRights(chat.admin_rights);
        }
        if (!TextUtils.isEmpty(o.title)) {
            chat.title = o.title;
        }
        if (!TextUtils.isEmpty(o.username)) {
            chat.username = o.username;
            if (chat.usernames == null) {
                chat.usernames = new ArrayList<>();
            }
            boolean found = false;
            for (int i = 0; i < chat.usernames.size(); i++) {
                TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && u.editable) {
                    u.username = o.username;
                    u.active = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                TLRPC.TL_username u = new TLRPC.TL_username();
                u.username = o.username;
                u.active = true;
                u.editable = true;
                chat.usernames.add(0, u);
            }
        }
        if (o.participantsCount > 0) {
            chat.participants_count = o.participantsCount;
            chat.flags |= 64;
        }
    }

    public static int membersCount(TLRPC.Chat chat, TLRPC.ChatFull chatInfo) {
        if (chat != null && isEnabled(chat.id)) {
            Overlay o = CACHE.get(chat.id);
            if (o != null && o.participantsCount > 0) {
                return o.participantsCount;
            }
        }
        if (chatInfo != null && chatInfo.participants_count > 0) {
            return chatInfo.participants_count;
        }
        return chat != null ? chat.participants_count : 0;
    }

    /** Count shown on profile "Administrators" row (includes self as owner). */
    public static int adminsCount(long chatId, TLRPC.ChatFull chatInfo) {
        if (isEnabled(chatId)) {
            int n = fakeAdminTokens(chatId).size();
            long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            boolean selfListed = false;
            for (String token : fakeAdminTokens(chatId)) {
                if (token.equals(String.valueOf(selfId))) {
                    selfListed = true;
                    break;
                }
            }
            if (!selfListed) {
                n++; // local self as creator
            }
            // Merge remote admins from relay
            ArrayList<Long> remote = LarpChannelRelay.remoteAdminIds(chatId);
            for (int i = 0; i < remote.size(); i++) {
                long id = remote.get(i);
                if (id == selfId) {
                    continue;
                }
                boolean found = false;
                for (String token : fakeAdminTokens(chatId)) {
                    if (token.equals(String.valueOf(id))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    n++;
                }
            }
            return Math.max(1, n);
        }
        return chatInfo != null ? chatInfo.admins_count : 0;
    }

    public static String displayTitle(TLRPC.Chat chat) {
        if (chat == null) {
            return "";
        }
        if (isEnabled(chat.id)) {
            Overlay o = CACHE.get(chat.id);
            if (o != null && !TextUtils.isEmpty(o.title)) {
                return o.title;
            }
        }
        return chat.title;
    }

    public static String displayUsername(TLRPC.Chat chat) {
        if (chat == null) {
            return null;
        }
        if (isEnabled(chat.id)) {
            Overlay o = CACHE.get(chat.id);
            if (o != null && !TextUtils.isEmpty(o.username)) {
                return o.username;
            }
        }
        return chat.username;
    }

    public static String photoPath(long chatId) {
        if (!isEnabled(chatId)) {
            return null;
        }
        Overlay o = CACHE.get(chatId);
        return o != null && !TextUtils.isEmpty(o.photoPath) ? o.photoPath : null;
    }

    public static ArrayList<String> fakeAdminTokens(long chatId) {
        ArrayList<String> result = new ArrayList<>();
        if (!isEnabled(chatId)) {
            return result;
        }
        Overlay o = CACHE.get(chatId);
        if (o == null || TextUtils.isEmpty(o.fakeAdmins)) {
            return result;
        }
        for (String token : o.fakeAdmins.split("[,;\\n]+")) {
            String t = token == null ? "" : token.trim();
            if (t.startsWith("@")) {
                t = t.substring(1);
            }
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }

    public static void addFakeAdmin(long chatId, TLRPC.User user) {
        if (chatId == 0 || user == null || user.id == 0) {
            return;
        }
        ensureLoaded();
        Overlay existing = CACHE.get(chatId);
        Overlay o = existing != null ? existing.copy() : new Overlay();
        o.chatId = chatId;
        o.enabled = true;
        o.forceCreator = true;
        ArrayList<String> tokens = new ArrayList<>();
        if (!TextUtils.isEmpty(o.fakeAdmins)) {
            for (String token : o.fakeAdmins.split("[,;\\n]+")) {
                String t = token == null ? "" : token.trim();
                if (t.startsWith("@")) {
                    t = t.substring(1);
                }
                if (!t.isEmpty() && !tokens.contains(t)) {
                    tokens.add(t);
                }
            }
        }
        String idToken = String.valueOf(user.id);
        // Prefer stable id; drop username duplicate of same user.
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equalsIgnoreCase(idToken)) {
                tokens.remove(i--);
                continue;
            }
            if (!TextUtils.isEmpty(user.username) && t.equalsIgnoreCase(user.username)) {
                tokens.remove(i--);
            }
        }
        tokens.add(0, idToken);
        o.fakeAdmins = TextUtils.join(", ", tokens);
        put(o);
    }

    public static void removeFakeAdmin(long chatId, TLRPC.User user) {
        if (chatId == 0 || user == null) {
            return;
        }
        ensureLoaded();
        Overlay existing = CACHE.get(chatId);
        if (existing == null || TextUtils.isEmpty(existing.fakeAdmins)) {
            return;
        }
        Overlay o = existing.copy();
        ArrayList<String> tokens = new ArrayList<>();
        String idToken = String.valueOf(user.id);
        String username = user.username;
        for (String token : o.fakeAdmins.split("[,;\\n]+")) {
            String t = token == null ? "" : token.trim();
            if (t.startsWith("@")) {
                t = t.substring(1);
            }
            if (t.isEmpty()) {
                continue;
            }
            if (t.equalsIgnoreCase(idToken)) {
                continue;
            }
            if (!TextUtils.isEmpty(username) && t.equalsIgnoreCase(username)) {
                continue;
            }
            tokens.add(t);
        }
        o.fakeAdmins = TextUtils.join(", ", tokens);
        put(o);
    }

    /** Resolve unknown @usernames from fakeAdmins and rewrite them to user ids. */
    public static void resolveFakeAdmins(int account, long chatId, Runnable done) {
        if (!isEnabled(chatId)) {
            if (done != null) {
                done.run();
            }
            return;
        }
        ArrayList<String> tokens = fakeAdminTokens(chatId);
        if (tokens.isEmpty()) {
            if (done != null) {
                done.run();
            }
            return;
        }
        MessagesController mc = MessagesController.getInstance(account);
        final int[] pending = new int[]{0};
        final boolean[] changed = new boolean[]{false};
        Overlay o = get(chatId);
        if (o == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        final Overlay working = o.copy();
        ArrayList<String> out = new ArrayList<>();
        Runnable maybeFinish = () -> {
            if (pending[0] > 0) {
                return;
            }
            if (changed[0]) {
                working.fakeAdmins = TextUtils.join(", ", out);
                put(working);
            }
            if (done != null) {
                done.run();
            }
        };
        for (String token : tokens) {
            long userId = 0;
            try {
                userId = Long.parseLong(token);
            } catch (Exception ignored) {
            }
            if (userId != 0) {
                if (!out.contains(String.valueOf(userId))) {
                    out.add(String.valueOf(userId));
                }
                continue;
            }
            TLRPC.User cached = mc.getUser(token.toLowerCase(Locale.ROOT));
            if (cached != null) {
                String id = String.valueOf(cached.id);
                if (!out.contains(id)) {
                    out.add(id);
                }
                changed[0] = true;
                continue;
            }
            pending[0]++;
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = token;
            final String tokenFinal = token;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                    TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                    mc.putUsers(resolved.users, false);
                    mc.putChats(resolved.chats, false);
                    long uid = 0;
                    if (resolved.peer instanceof TLRPC.TL_peerUser) {
                        uid = ((TLRPC.TL_peerUser) resolved.peer).user_id;
                    } else if (resolved.users != null && !resolved.users.isEmpty()) {
                        uid = resolved.users.get(0).id;
                    }
                    if (uid != 0) {
                        String id = String.valueOf(uid);
                        if (!out.contains(id)) {
                            out.add(id);
                        }
                        changed[0] = true;
                    } else if (!out.contains(tokenFinal)) {
                        out.add(tokenFinal);
                    }
                } else if (!out.contains(tokenFinal)) {
                    out.add(tokenFinal);
                }
                pending[0]--;
                maybeFinish.run();
            }));
        }
        maybeFinish.run();
    }

    public static TLRPC.TL_chatAdminRights fullAdminRights() {
        TLRPC.TL_chatAdminRights r = new TLRPC.TL_chatAdminRights();
        fillAdminRights(r);
        return r;
    }

    public static void fillAdminRights(TLRPC.TL_chatAdminRights r) {
        if (r == null) {
            return;
        }
        r.change_info = true;
        r.post_messages = true;
        r.edit_messages = true;
        r.delete_messages = true;
        r.ban_users = true;
        r.invite_users = true;
        r.pin_messages = true;
        r.add_admins = true;
        r.anonymous = false;
        r.manage_call = true;
        r.other = true;
        r.manage_topics = true;
        r.post_stories = true;
        r.edit_stories = true;
        r.delete_stories = true;
        r.manage_direct_messages = true;
        r.manage_ranks = true;
        // flags bitmask used by hasAdminRights
        r.flags = ~0;
    }

    public static void resolveAndAdd(int account, String link, ResolveCallback callback) {
        final String username = LarpPersonalChannel.parseChannelUsername(link);
        if (TextUtils.isEmpty(username)) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(false, null, "empty"));
            }
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                if (callback != null) {
                    callback.onResult(false, null, error != null ? error.text : "resolve_failed");
                }
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            controller.putUsers(resolved.users, false);
            controller.putChats(resolved.chats, false);
            long channelId = 0;
            if (resolved.peer instanceof TLRPC.TL_peerChannel) {
                channelId = ((TLRPC.TL_peerChannel) resolved.peer).channel_id;
            } else if (resolved.peer instanceof TLRPC.TL_peerChat) {
                channelId = ((TLRPC.TL_peerChat) resolved.peer).chat_id;
            }
            if (channelId == 0 && resolved.chats != null && !resolved.chats.isEmpty()) {
                channelId = resolved.chats.get(0).id;
            }
            if (channelId == 0) {
                if (callback != null) {
                    callback.onResult(false, null, "not_channel");
                }
                return;
            }
            Overlay existing = get(channelId);
            Overlay o = existing != null ? existing.copy() : new Overlay();
            o.chatId = channelId;
            o.link = link == null ? "" : link.trim();
            o.enabled = true;
            o.forceCreator = true;
            TLRPC.Chat chat = controller.getChat(channelId);
            if (chat != null) {
                applyDisplay(chat);
            }
            put(o);
            if (callback != null) {
                callback.onResult(true, o, "ok");
            }
        }));
    }

    /** Merge fake admin channel participants into list (TYPE_ADMIN). */
    public static void injectFakeAdmins(int account, long chatId, ArrayList<TLObject> participants, LongSparsePut putMap) {
        if (!isEnabled(chatId) || participants == null) {
            return;
        }
        ArrayList<String> tokens = fakeAdminTokens(chatId);
        for (Long rid : LarpChannelRelay.remoteAdminIds(chatId)) {
            if (rid == null || rid == 0) continue;
            String s = String.valueOf(rid);
            if (!tokens.contains(s)) {
                tokens.add(s);
            }
        }
        if (tokens.isEmpty()) {
            // Always inject self as creator admin for visual
            injectSelfAsAdmin(account, chatId, participants, putMap);
            return;
        }
        MessagesController mc = MessagesController.getInstance(account);
        for (String token : tokens) {
            long userId = 0;
            try {
                userId = Long.parseLong(token);
            } catch (Exception ignored) {
            }
            TLRPC.User user = null;
            if (userId != 0) {
                user = mc.getUser(userId);
                if (user == null) {
                    // Placeholder so admin list cells don't NPE / skip rendering.
                    TLRPC.TL_user placeholder = new TLRPC.TL_user();
                    placeholder.id = userId;
                    placeholder.first_name = "User " + userId;
                    mc.putUser(placeholder, false);
                    user = placeholder;
                }
            } else {
                user = mc.getUser(token.toLowerCase(Locale.ROOT));
                if (user == null) {
                    // try resolve later; skip silently if unknown
                    continue;
                }
                userId = user.id;
            }
            if (userId == 0) {
                continue;
            }
            if (putMap != null && putMap.contains(userId)) {
                continue;
            }
            boolean exists = false;
            for (int i = 0; i < participants.size(); i++) {
                TLObject p = participants.get(i);
                long pid = peerIdOf(p);
                if (pid == userId) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                continue;
            }
            TLRPC.TL_channelParticipantAdmin admin = new TLRPC.TL_channelParticipantAdmin();
            admin.user_id = userId;
            admin.peer = new TLRPC.TL_peerUser();
            admin.peer.user_id = userId;
            admin.admin_rights = fullAdminRights();
            admin.rank = "LARP";
            admin.date = (int) (System.currentTimeMillis() / 1000L);
            participants.add(0, admin);
            if (putMap != null) {
                putMap.put(userId, admin);
            }
        }
        injectSelfAsAdmin(account, chatId, participants, putMap);
    }

    private static void injectSelfAsAdmin(int account, long chatId, ArrayList<TLObject> participants, LongSparsePut putMap) {
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId == 0) {
            return;
        }
        if (putMap != null && putMap.contains(selfId)) {
            return;
        }
        for (int i = 0; i < participants.size(); i++) {
            if (peerIdOf(participants.get(i)) == selfId) {
                return;
            }
        }
        TLRPC.TL_channelParticipantCreator creator = new TLRPC.TL_channelParticipantCreator();
        creator.user_id = selfId;
        creator.peer = new TLRPC.TL_peerUser();
        creator.peer.user_id = selfId;
        creator.admin_rights = fullAdminRights();
        creator.rank = "Owner";
        participants.add(0, creator);
        if (putMap != null) {
            putMap.put(selfId, creator);
        }
    }

    private static long peerIdOf(TLObject p) {
        if (p instanceof TLRPC.ChannelParticipant) {
            TLRPC.ChannelParticipant cp = (TLRPC.ChannelParticipant) p;
            if (cp.peer != null && cp.peer.user_id != 0) {
                return cp.peer.user_id;
            }
            return cp.user_id;
        }
        if (p instanceof TLRPC.ChatParticipant) {
            return ((TLRPC.ChatParticipant) p).user_id;
        }
        return 0;
    }

    /** Tiny adapter so we don't depend on LongSparseArray import shape in helper API. */
    public interface LongSparsePut {
        boolean contains(long key);
        void put(long key, TLObject value);
    }

    private static void notifyChanged(long chatId) {
        int account = UserConfig.selectedAccount;
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
        if (chat != null) {
            applyDisplay(chat);
        }
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_CHAT_NAME | MessagesController.UPDATE_MASK_CHAT_MEMBERS | MessagesController.UPDATE_MASK_CHAT);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY, "");
            if (TextUtils.isEmpty(raw)) {
                return;
            }
            JSONObject root = new JSONObject(raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject j = root.optJSONObject(k);
                if (j == null) {
                    continue;
                }
                Overlay o = fromJson(j);
                if (o.chatId != 0) {
                    CACHE.put(o.chatId, o);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static synchronized void save() {
        try {
            JSONObject root = new JSONObject();
            for (Overlay o : CACHE.values()) {
                root.put(String.valueOf(o.chatId), toJson(o));
            }
            ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, root.toString())
                    .apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static JSONObject toJson(Overlay o) throws Exception {
        JSONObject j = new JSONObject();
        j.put("chatId", o.chatId);
        j.put("link", o.link != null ? o.link : "");
        j.put("enabled", o.enabled);
        j.put("forceCreator", o.forceCreator);
        j.put("title", o.title != null ? o.title : "");
        j.put("username", o.username != null ? o.username : "");
        j.put("photoPath", o.photoPath != null ? o.photoPath : "");
        j.put("participantsCount", o.participantsCount);
        j.put("fakeAdmins", o.fakeAdmins != null ? o.fakeAdmins : "");
        JSONArray posts = new JSONArray();
        if (o.posts != null) {
            for (LarpChannelRelay.FakePost p : o.posts) {
                JSONObject jp = new JSONObject();
                jp.put("id", p.id);
                jp.put("from_user_id", p.fromUserId);
                jp.put("text", p.text != null ? p.text : "");
                jp.put("date", p.date);
                posts.put(jp);
            }
        }
        j.put("posts", posts);
        return j;
    }

    private static Overlay fromJson(JSONObject j) {
        Overlay o = new Overlay();
        o.chatId = j.optLong("chatId", 0);
        o.link = j.optString("link", "");
        o.enabled = j.optBoolean("enabled", true);
        o.forceCreator = j.optBoolean("forceCreator", true);
        o.title = j.optString("title", "");
        o.username = j.optString("username", "");
        o.photoPath = j.optString("photoPath", "");
        o.participantsCount = j.optInt("participantsCount", 0);
        o.fakeAdmins = j.optString("fakeAdmins", "");
        o.posts = new ArrayList<>();
        JSONArray posts = j.optJSONArray("posts");
        if (posts != null) {
            for (int i = 0; i < posts.length(); i++) {
                JSONObject jp = posts.optJSONObject(i);
                if (jp == null) continue;
                LarpChannelRelay.FakePost p = new LarpChannelRelay.FakePost();
                p.id = jp.optLong("id", 0);
                p.fromUserId = jp.optLong("from_user_id", 0);
                p.text = jp.optString("text", "");
                p.date = jp.optInt("date", 0);
                o.posts.add(p);
            }
        }
        return o;
    }
}

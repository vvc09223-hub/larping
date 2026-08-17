package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [MG] Publish / fetch LARP profile fields so other MercuryGram users see fake usernames & level.
 */
public final class LarpProfileRelay {

    public static final class RemoteProfile {
        public String usernames = "";
        public String phone = "";
        public String personalChannel = "";
        public long personalChannelId;
        public int personalChannelMessageId;
        public int level;
        public boolean hold;
        public boolean major;
        public long updatedAt;
    }

    private static final ConcurrentHashMap<Long, RemoteProfile> CACHE = new ConcurrentHashMap<>();

    private LarpProfileRelay() {
    }

    public static void publishSelf(int account) {
        if (!LicenseManager.isActive() || !SharedConfig.larpVisualMenu) {
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
                body.put("usernames", filterActiveUsernames(SharedConfig.fakeFragmentUsernames));
                body.put("phone", SharedConfig.fakePhoneNumber != null
                        ? SharedConfig.fakePhoneNumber : "");
                body.put("level", SharedConfig.fakeProfileLevel);
                body.put("hold", SharedConfig.fakeProfileHold);
                body.put("major", SharedConfig.fakeProfileMajor);
                body.put("personal_channel", SharedConfig.fakePersonalChannelEnabled
                        && !TextUtils.isEmpty(SharedConfig.fakePersonalChannelLink)
                        ? SharedConfig.fakePersonalChannelLink : "");
                body.put("personal_channel_id", SharedConfig.fakePersonalChannelEnabled
                        ? SharedConfig.fakePersonalChannelId : 0);
                body.put("personal_channel_message_id", SharedConfig.fakePersonalChannelEnabled
                        ? SharedConfig.fakePersonalChannelMessageId : 0);
                LicenseManager.postJson("/api/profile/publish", body, null);
            } catch (Exception ignored) {
            }
        });
    }

    public static void fetch(int account, long userId) {
        if (!LicenseManager.isActive() || userId <= 0) {
            return;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        if (userId == selfId) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final JSONObject body = new JSONObject();
                body.put("key", SharedConfig.larpLicenseKey);
                body.put("hwid", LicenseManager.getHardwareId());
                body.put("target_user_id", userId);
                final JSONObject response = LicenseManager.postJsonSync("/api/profile/get", body);
                if (response == null || !response.optBoolean("ok", false)) {
                    return;
                }
                final JSONObject profile = response.optJSONObject("profile");
                if (profile == null) {
                    CACHE.remove(userId);
                    return;
                }
                final RemoteProfile remote = new RemoteProfile();
                remote.usernames = profile.optString("usernames", "");
                remote.phone = profile.optString("phone", "");
                remote.personalChannel = profile.optString("personal_channel", "");
                remote.personalChannelId = profile.optLong("personal_channel_id", 0);
                remote.personalChannelMessageId = profile.optInt("personal_channel_message_id", 0);
                remote.level = profile.optInt("level", 0);
                remote.hold = profile.optBoolean("hold", false);
                remote.major = profile.optBoolean("major", false);
                remote.updatedAt = profile.optLong("updated_at", 0);
                if (TextUtils.isEmpty(remote.usernames)
                        && TextUtils.isEmpty(remote.phone)
                        && TextUtils.isEmpty(remote.personalChannel)
                        && remote.personalChannelId <= 0
                        && remote.level <= 0
                        && !remote.hold
                        && !remote.major) {
                    CACHE.remove(userId);
                } else {
                    CACHE.put(userId, remote);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (remote.personalChannelId != 0 || !TextUtils.isEmpty(remote.personalChannel)) {
                        resolveRemoteChannel(account, userId, remote);
                    } else {
                        notifyProfileRefresh(account, userId);
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    private static void resolveRemoteChannel(int account, long userId, RemoteProfile remote) {
        if (remote.personalChannelId != 0) {
            notifyProfileRefresh(account, userId);
            return;
        }
        final String username = LarpPersonalChannel.parseChannelUsername(remote.personalChannel);
        if (TextUtils.isEmpty(username)) {
            notifyProfileRefresh(account, userId);
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                controller.putUsers(resolved.users, false);
                controller.putChats(resolved.chats, false);
                long channelId = 0;
                if (resolved.peer instanceof TLRPC.TL_peerChannel) {
                    channelId = ((TLRPC.TL_peerChannel) resolved.peer).channel_id;
                } else if (resolved.chats != null && !resolved.chats.isEmpty()) {
                    channelId = resolved.chats.get(0).id;
                }
                if (channelId != 0) {
                    remote.personalChannelId = channelId;
                    CACHE.put(userId, remote);
                }
            }
            notifyProfileRefresh(account, userId);
        }));
    }

    public static RemoteProfile getCached(long userId) {
        return CACHE.get(userId);
    }

    public static ArrayList<TLRPC.TL_username> usernamesForUser(long userId) {
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == selfId) {
            if (!SharedConfig.fakeUsernamesShow) {
                return new ArrayList<>();
            }
            return parseUsernames(filterActiveUsernames(SharedConfig.fakeFragmentUsernames));
        }
        final RemoteProfile remote = CACHE.get(userId);
        if (remote != null && !TextUtils.isEmpty(remote.usernames)) {
            return parseUsernames(remote.usernames);
        }
        return new ArrayList<>();
    }

    /** All LARP usernames for the Username settings screen (including hidden/inactive). */
    public static ArrayList<TLRPC.TL_username> larpUsernamesForEditList() {
        final ArrayList<TLRPC.TL_username> result = new ArrayList<>();
        if (!LicenseManager.isActive() || !SharedConfig.larpVisualMenu || !SharedConfig.fakeUsernamesShow) {
            return result;
        }
        if (TextUtils.isEmpty(SharedConfig.fakeFragmentUsernames)) {
            return result;
        }
        for (String token : SharedConfig.fakeFragmentUsernames.split("[,;\\s\\n]+")) {
            String username = normalizeUsername(token);
            if (username.isEmpty()) {
                continue;
            }
            final TLRPC.TL_username item = new TLRPC.TL_username();
            item.username = username;
            item.active = isUsernameEnabled(username);
            item.editable = false;
            result.add(item);
        }
        return result;
    }

    public static boolean isLarpUsername(String username) {
        final String normalized = normalizeUsername(username);
        if (normalized.isEmpty() || TextUtils.isEmpty(SharedConfig.fakeFragmentUsernames)) {
            return false;
        }
        for (String token : SharedConfig.fakeFragmentUsernames.split("[,;\\s\\n]+")) {
            if (normalized.equalsIgnoreCase(normalizeUsername(token))) {
                return true;
            }
        }
        return false;
    }

    /** Patch client user.usernames so all LARP names are visible app-wide. */
    public static void applySelfUsernames(int account) {
        if (!LicenseManager.isActive() || !SharedConfig.larpVisualMenu) {
            return;
        }
        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            return;
        }
        final ArrayList<TLRPC.TL_username> larp = usernamesForUser(user.id);
        if (larp.isEmpty()) {
            return;
        }
        if (user.usernames == null) {
            user.usernames = new ArrayList<>();
        }
        for (TLRPC.TL_username item : larp) {
            boolean exists = false;
            for (int i = 0; i < user.usernames.size(); i++) {
                TLRPC.TL_username u = user.usernames.get(i);
                if (u != null && u.username != null && u.username.equalsIgnoreCase(item.username)) {
                    u.active = item.active;
                    u.editable = false;
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                user.usernames.add(item);
            }
        }
        // Prefer first active LARP username as the primary public username.
        for (TLRPC.TL_username item : larp) {
            if (item.active && !TextUtils.isEmpty(item.username)) {
                user.username = item.username;
                break;
            }
        }
        MessagesController.getInstance(account).putUser(user, false);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    public static String phoneForUser(long userId, String fallbackPhone) {
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == selfId && !TextUtils.isEmpty(SharedConfig.fakePhoneNumber)) {
            return SharedConfig.fakePhoneNumber;
        }
        final RemoteProfile remote = CACHE.get(userId);
        if (remote != null && !TextUtils.isEmpty(remote.phone)) {
            return remote.phone;
        }
        return fallbackPhone;
    }

    public static int levelForUser(long userId) {
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == selfId) {
            return SharedConfig.fakeProfileLevel;
        }
        final RemoteProfile remote = CACHE.get(userId);
        return remote != null ? remote.level : 0;
    }

    public static boolean holdForUser(long userId) {
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == selfId) {
            return SharedConfig.fakeProfileHold;
        }
        final RemoteProfile remote = CACHE.get(userId);
        return remote != null && remote.hold;
    }

    public static boolean majorForUser(long userId) {
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == selfId) {
            return SharedConfig.fakeProfileMajor;
        }
        final RemoteProfile remote = CACHE.get(userId);
        return remote != null && remote.major;
    }

    public static TL_stars.Tl_starsRating applyFakeLevel(long userId, TL_stars.Tl_starsRating rating) {
        final int level = levelForUser(userId);
        final boolean hold = holdForUser(userId);
        final boolean major = majorForUser(userId);
        if (level <= 0 && !hold && !major) {
            return rating;
        }
        final TL_stars.Tl_starsRating patched = new TL_stars.Tl_starsRating();
        patched.flags = rating != null ? rating.flags : 0;
        patched.level = level > 0 ? level : (rating != null ? rating.level : 1);
        patched.current_level_stars = patched.level * 100L;
        patched.stars = patched.current_level_stars;
        patched.next_level_stars = (patched.level + 1) * 100L;
        patched.flags = TLObject.setFlag(patched.flags, TLObject.FLAG_0, true);
        patched.flags = TLObject.setFlag(patched.flags, TLObject.FLAG_1, hold);
        patched.flags = TLObject.setFlag(patched.flags, TLObject.FLAG_2, major);
        return patched;
    }

    public static void notifyProfileRefresh(int account, long userId) {
        final TLRPC.UserFull userFull = MessagesController.getInstance(account).getUserFull(userId);
        if (userFull != null) {
            LarpPersonalChannel.applyFakeChannel(userId, userFull);
            NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.userInfoDidLoad, userId, userFull);
        }
    }

    public static String filterActiveUsernames(String raw) {
        if (TextUtils.isEmpty(raw) || !SharedConfig.fakeUsernamesShow) {
            return "";
        }
        final HashSet<String> disabled = disabledSet();
        final ArrayList<String> result = new ArrayList<>();
        for (String token : raw.split("[,;\\s\\n]+")) {
            String username = normalizeUsername(token);
            if (username.isEmpty() || disabled.contains(username.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(username);
        }
        return TextUtils.join(", ", result);
    }

    public static boolean isUsernameEnabled(String username) {
        final String normalized = normalizeUsername(username);
        if (normalized.isEmpty()) {
            return false;
        }
        return !disabledSet().contains(normalized.toLowerCase(Locale.ROOT));
    }

    public static void setUsernameActive(String username, boolean active) {
        final String normalized = normalizeUsername(username);
        if (normalized.isEmpty()) {
            return;
        }
        final HashSet<String> disabled = disabledSet();
        final String key = normalized.toLowerCase(Locale.ROOT);
        if (active) {
            disabled.remove(key);
        } else {
            disabled.add(key);
        }
        SharedConfig.setFakeDisabledUsernames(TextUtils.join(", ", disabled));
    }

    private static HashSet<String> disabledSet() {
        final HashSet<String> disabled = new HashSet<>();
        if (TextUtils.isEmpty(SharedConfig.fakeDisabledUsernames)) {
            return disabled;
        }
        for (String token : SharedConfig.fakeDisabledUsernames.split("[,;\\s\\n]+")) {
            final String username = normalizeUsername(token);
            if (!username.isEmpty()) {
                disabled.add(username.toLowerCase(Locale.ROOT));
            }
        }
        return disabled;
    }

    private static String normalizeUsername(String token) {
        String username = token == null ? "" : token.trim();
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        return username.trim();
    }

    public static ArrayList<TLRPC.TL_username> parseUsernames(String raw) {
        final ArrayList<TLRPC.TL_username> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        final HashSet<String> seen = new HashSet<>();
        for (String token : raw.split("[,;\\s\\n]+")) {
            String username = normalizeUsername(token);
            if (username.isEmpty()) {
                continue;
            }
            final String key = username.toLowerCase(Locale.ROOT);
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            final TLRPC.TL_username item = new TLRPC.TL_username();
            item.username = username;
            item.active = isUsernameEnabled(username);
            item.editable = false;
            if (item.active) {
                result.add(item);
            }
        }
        return result;
    }
}

package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [MG] Bind a Telegram channel/link as personal channel in profile (AyuGram Desktop parity).
 */
public final class LarpPersonalChannel {

    private static final Pattern[] USERNAME_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)(?:https?://)?t\\.me/([A-Za-z0-9_]+)"),
            Pattern.compile("(?i)(?:https?://)?telegram\\.me/([A-Za-z0-9_]+)"),
            Pattern.compile("(?i)^@?([A-Za-z0-9_]+)$"),
    };

    private LarpPersonalChannel() {
    }

    public static String parseChannelUsername(String link) {
        if (TextUtils.isEmpty(link)) {
            return "";
        }
        final String value = link.trim();
        for (Pattern pattern : USERNAME_PATTERNS) {
            final Matcher match = pattern.matcher(value);
            if (match.find()) {
                final String username = match.group(1);
                if (!TextUtils.isEmpty(username)) {
                    return username;
                }
            }
        }
        String fallback = value;
        if (fallback.startsWith("@")) {
            fallback = fallback.substring(1);
        }
        return fallback.trim();
    }

    public static void apply(int account) {
        if (!LicenseManager.isActive() || !SharedConfig.larpVisualMenu) {
            return;
        }
        if (!SharedConfig.fakePersonalChannelEnabled
                || TextUtils.isEmpty(SharedConfig.fakePersonalChannelLink)) {
            notifySelf(account);
            return;
        }
        if (SharedConfig.fakePersonalChannelId != 0) {
            applyToUserFull(account, SharedConfig.fakePersonalChannelId, SharedConfig.fakePersonalChannelMessageId);
            notifySelf(account);
        }
        final String username = parseChannelUsername(SharedConfig.fakePersonalChannelLink);
        if (TextUtils.isEmpty(username)) {
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                notifySelf(account);
                return;
            }
            final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
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
                notifySelf(account);
                return;
            }
            setFromChannel(account, channelId);
        }));
    }

    public static void applyToUserFull(int account, long channelId, int messageId) {
        if (channelId == 0) {
            return;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        final TLRPC.UserFull userFull = MessagesController.getInstance(account).getUserFull(selfId);
        if (userFull == null) {
            return;
        }
        userFull.personal_channel_id = channelId;
        userFull.personal_channel_message = messageId;
        userFull.flags2 = TLObject.setFlag(userFull.flags2, TLObject.FLAG_6, true);
        if (SharedConfig.fakePersonalChannelSubscribers > 1) {
            final TLRPC.Chat channel = MessagesController.getInstance(account).getChat(channelId);
            if (channel != null) {
                channel.participants_count = SharedConfig.fakePersonalChannelSubscribers;
                channel.flags |= 64;
            }
        }
    }

    public static boolean applyFakeChannel(long userId, TLRPC.UserFull userFull) {
        if (userFull == null || !LicenseManager.isActive() || !SharedConfig.larpVisualMenu) {
            return false;
        }
        final long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == selfId) {
            if (!SharedConfig.fakePersonalChannelEnabled || SharedConfig.fakePersonalChannelId == 0) {
                return false;
            }
            userFull.personal_channel_id = SharedConfig.fakePersonalChannelId;
            userFull.personal_channel_message = SharedConfig.fakePersonalChannelMessageId;
            userFull.flags2 = TLObject.setFlag(userFull.flags2, TLObject.FLAG_6, true);
            if (SharedConfig.fakePersonalChannelSubscribers > 1) {
                final TLRPC.Chat channel = MessagesController.getInstance(UserConfig.selectedAccount)
                        .getChat(SharedConfig.fakePersonalChannelId);
                if (channel != null) {
                    channel.participants_count = SharedConfig.fakePersonalChannelSubscribers;
                    channel.flags |= 64;
                }
            }
            return true;
        }
        final LarpProfileRelay.RemoteProfile remote = LarpProfileRelay.getCached(userId);
        if (remote == null || remote.personalChannelId == 0) {
            return false;
        }
        userFull.personal_channel_id = remote.personalChannelId;
        userFull.personal_channel_message = remote.personalChannelMessageId;
        userFull.flags2 = TLObject.setFlag(userFull.flags2, TLObject.FLAG_6, true);
        return true;
    }

    private static void setFromChannel(int account, long channelId) {
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.Chat channel = controller.getChat(channelId);
        if (channel == null || !ChatObject.isChannel(channel)) {
            SharedConfig.setFakePersonalChannelIds(channelId, 0);
            applyToUserFull(account, channelId, 0);
            notifySelf(account);
            LarpProfileRelay.publishSelf(account);
            return;
        }
        final TLRPC.TL_messages_getHistory history = new TLRPC.TL_messages_getHistory();
        history.peer = controller.getInputPeer(-channelId);
        history.offset_id = 0;
        history.offset_date = 0;
        history.add_offset = 0;
        history.limit = 1;
        history.max_id = 0;
        history.min_id = 0;
        history.hash = 0;
        ConnectionsManager.getInstance(account).sendRequest(history, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            int messageId = 0;
            if (response instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                controller.putUsers(messages.users, false);
                controller.putChats(messages.chats, false);
                if (messages.messages != null && !messages.messages.isEmpty()) {
                    messageId = messages.messages.get(0).id;
                }
            }
            SharedConfig.setFakePersonalChannelIds(channelId, messageId);
            applyToUserFull(account, channelId, messageId);
            notifySelf(account);
            LarpProfileRelay.publishSelf(account);
        }));
    }

    private static void notifySelf(int account) {
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        final TLRPC.UserFull userFull = MessagesController.getInstance(account).getUserFull(selfId);
        if (userFull != null) {
            applyFakeChannel(selfId, userFull);
        }
        NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.userInfoDidLoad, selfId, userFull);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mainUserInfoChanged);
    }
}

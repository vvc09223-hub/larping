package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

/**
 * [MG] Display names for LARP gift messages (sender nickname, not app branding).
 */
public final class LarpSenderNames {

    private LarpSenderNames() {
    }

    public static String fromProfile(JSONObject profile) {
        if (profile == null) {
            return "";
        }
        final String first = profile.optString("first_name", "").trim();
        final String last = profile.optString("last_name", "").trim();
        final String name = (first + " " + last).trim();
        if (!name.isEmpty()) {
            return name;
        }
        final JSONArray usernames = profile.optJSONArray("usernames");
        if (usernames != null && usernames.length() > 0) {
            final String u = usernames.optString(0, "").trim();
            if (!u.isEmpty()) {
                return u.startsWith("@") ? u.substring(1) : u;
            }
        }
        final String username = profile.optString("username", "").trim();
        if (!username.isEmpty()) {
            return username.startsWith("@") ? username.substring(1) : username;
        }
        return "";
    }

    public static String fromUserId(int account, long userId) {
        if (userId <= 0) {
            return "";
        }
        final TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
        if (user != null) {
            return UserObject.getUserName(user);
        }
        return "";
    }

    /** User-visible note under gift card; empty = only "Gift from &lt;name&gt;" is shown. */
    public static String giftNoteFromProfile(JSONObject profile, String userMessage) {
        if (!TextUtils.isEmpty(userMessage)) {
            return userMessage.trim();
        }
        return "";
    }
}

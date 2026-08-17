package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

import tw.nekomimi.nekogram.helpers.LarpPaymentHelper;
import tw.nekomimi.nekogram.helpers.LicenseManager;

public class LarpFunctionsActivity extends UniversalFragment {

    private static final int ITEM_LICENSE = 0;
    private static final int ITEM_LARP_MENU = 10;
    private static final int ITEM_LARP_GIFTS = 11;
    private static final int ITEM_FAKE_STARS = 1;
    private static final int ITEM_FAKE_TON = 2;
    private static final int ITEM_FAKE_NFTS = 3;
    private static final int ITEM_FAKE_USERNAMES = 4;
    private static final int ITEM_FAKE_PHONE = 5;
    private static final int ITEM_FAKE_LEVEL = 6;
    private static final int ITEM_FAKE_HOLD = 7;
    private static final int ITEM_FAKE_MAJOR = 8;
    private static final int ITEM_TEST_CARDS = 9;
    private static final int ITEM_FAKE_USERNAMES_SHOW = 12;
    private static final int ITEM_FAKE_USERNAMES_HIDE = 13;
    private static final int ITEM_FAKE_CHANNEL_ENABLE = 14;
    private static final int ITEM_FAKE_CHANNEL_LINK = 15;
    private static final int ITEM_FAKE_CHANNEL_SUBS = 16;
    private static final int ITEM_ADMIN_ADD = 17;
    private static final int ITEM_ADMIN_ROW = 10000;

    private String licenseStatus = "";
    private ArrayList<String> testCards = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        LicenseManager.ensureActivated((ok, message) -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.LarpFunctionsTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String status = LicenseManager.isActive()
                ? getString(R.string.LarpLicenseActive)
                : (!TextUtils.isEmpty(licenseStatus)
                    ? licenseStatus
                    : (!TextUtils.isEmpty(SharedConfig.larpLicenseKey)
                        ? getString(R.string.LarpLicenseChecking)
                        : getString(R.string.LarpLicenseNeedKey)));
        items.add(UItem.asSettingsCell(ITEM_LICENSE, R.drawable.settings_privacy, getString(R.string.LarpLicenseTitle), status));

        if (!LicenseManager.isActive()) {
            if (!TextUtils.isEmpty(SharedConfig.larpLicenseKey)) {
                items.add(UItem.asShadow(getString(R.string.LarpLicenseNetworkError)));
            } else {
                items.add(UItem.asShadow(getString(R.string.LarpLicenseNeedKey)));
            }
            return;
        }

        items.add(UItem.asCheck(ITEM_LARP_MENU, getString(R.string.LarpVisualMenu)).setChecked(SharedConfig.larpVisualMenu));
        items.add(UItem.asCheck(ITEM_LARP_GIFTS, getString(R.string.LarpFakeGifts)).setChecked(SharedConfig.larpFakeGifts));
        if (testCards.isEmpty()) {
            refreshTestCards();
        }
        items.add(UItem.asSettingsCell(ITEM_TEST_CARDS, R.drawable.settings_privacy, getString(R.string.LarpTestCardsTitle), String.valueOf(testCards.size())));
        items.add(UItem.asShadow(getString(R.string.LarpTestCardsInfo)));

        items.add(UItem.asSettingsCell(ITEM_FAKE_STARS, R.drawable.settings_stars, getString(R.string.LarpFakeStarsTitle), SharedConfig.fakeStarsAmount > 0 ? String.valueOf(SharedConfig.fakeStarsAmount) : getString(R.string.LarpOff)));
        items.add(UItem.asSettingsCell(ITEM_FAKE_TON, R.drawable.settings_ton, getString(R.string.LarpFakeTonTitle), SharedConfig.fakeTonAmount > 0 ? String.valueOf(SharedConfig.fakeTonAmount) : getString(R.string.LarpOff)));
        items.add(UItem.asSettingsCell(ITEM_FAKE_NFTS, R.drawable.settings_gift, getString(R.string.LarpFakeNftsTitle), !TextUtils.isEmpty(SharedConfig.fakeNftSlug) ? SharedConfig.fakeNftSlug : getString(R.string.LarpOff)));
        items.add(UItem.asSettingsCell(ITEM_FAKE_USERNAMES, R.drawable.settings_account, getString(R.string.LarpFakeUsernamesTitle), !TextUtils.isEmpty(SharedConfig.fakeFragmentUsernames) ? SharedConfig.fakeFragmentUsernames : getString(R.string.LarpOff)));
        items.add(UItem.asCheck(ITEM_FAKE_USERNAMES_SHOW, getString(R.string.LarpFakeUsernamesShow)).setChecked(SharedConfig.fakeUsernamesShow));
        items.add(UItem.asSettingsCell(ITEM_FAKE_USERNAMES_HIDE, R.drawable.settings_privacy, getString(R.string.LarpFakeUsernamesHideTitle), !TextUtils.isEmpty(SharedConfig.fakeDisabledUsernames) ? SharedConfig.fakeDisabledUsernames : getString(R.string.LarpOff)));
        items.add(UItem.asSettingsCell(ITEM_FAKE_PHONE, R.drawable.settings_account, getString(R.string.LarpFakePhoneTitle), !TextUtils.isEmpty(SharedConfig.fakePhoneNumber) ? SharedConfig.fakePhoneNumber : getString(R.string.LarpOff)));
        items.add(UItem.asCheck(ITEM_FAKE_CHANNEL_ENABLE, getString(R.string.LarpFakeChannelEnable)).setChecked(SharedConfig.fakePersonalChannelEnabled));
        items.add(UItem.asSettingsCell(ITEM_FAKE_CHANNEL_LINK, R.drawable.settings_account, getString(R.string.LarpFakeChannelTitle), !TextUtils.isEmpty(SharedConfig.fakePersonalChannelLink) ? SharedConfig.fakePersonalChannelLink : getString(R.string.LarpOff)));
        items.add(UItem.asSettingsCell(ITEM_FAKE_CHANNEL_SUBS, R.drawable.settings_account, getString(R.string.LarpFakeChannelSubsTitle), SharedConfig.fakePersonalChannelSubscribers > 0 ? String.valueOf(SharedConfig.fakePersonalChannelSubscribers) : getString(R.string.LarpOff)));
        items.add(UItem.asSettingsCell(ITEM_FAKE_LEVEL, R.drawable.settings_stars, getString(R.string.LarpFakeLevelTitle), SharedConfig.fakeProfileLevel > 0 ? String.valueOf(SharedConfig.fakeProfileLevel) : getString(R.string.LarpOff)));
        items.add(UItem.asCheck(ITEM_FAKE_HOLD, getString(R.string.LarpFakeHoldTitle)).setChecked(SharedConfig.fakeProfileHold));
        items.add(UItem.asCheck(ITEM_FAKE_MAJOR, getString(R.string.LarpFakeMajorTitle)).setChecked(SharedConfig.fakeProfileMajor));
        items.add(UItem.asShadow(getString(R.string.LarpLocalAdminSection)));
        items.add(UItem.asSettingsCell(ITEM_ADMIN_ADD, R.drawable.msg_addbot, getString(R.string.LarpLocalAdminAdd), getString(R.string.LarpLocalAdminAddHint)));
        for (tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.Overlay o : tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.all()) {
            String title = !TextUtils.isEmpty(o.title) ? o.title : (!TextUtils.isEmpty(o.link) ? o.link : ("id:" + o.chatId));
            String value = o.enabled ? getString(R.string.LarpOn) : getString(R.string.LarpOff);
            if (o.participantsCount > 0) {
                value = value + " · " + o.participantsCount;
            }
            UItem row = UItem.asSettingsCell(ITEM_ADMIN_ROW, R.drawable.settings_account, title, value);
            row.dialogId = o.chatId;
            row.object = o;
            items.add(row);
        }
        items.add(UItem.asShadow(getString(R.string.LarpLocalAdminInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ITEM_LICENSE:
                showLicenseDialog();
                break;
            case ITEM_LARP_MENU:
                SharedConfig.setLarpVisualMenu(!SharedConfig.larpVisualMenu);
                if (listView != null) listView.adapter.update(true);
                break;
            case ITEM_LARP_GIFTS:
                SharedConfig.setLarpFakeGifts(!SharedConfig.larpFakeGifts);
                if (listView != null) listView.adapter.update(true);
                break;
            case ITEM_TEST_CARDS:
                showTestCardsDialog();
                break;
            case ITEM_FAKE_STARS:
                showFakeStarsDialog();
                break;
            case ITEM_FAKE_TON:
                showFakeTonDialog();
                break;
            case ITEM_FAKE_NFTS:
                showFakeNftsDialog();
                break;
            case ITEM_FAKE_USERNAMES:
                showFakeUsernamesDialog();
                break;
            case ITEM_FAKE_USERNAMES_SHOW:
                SharedConfig.setFakeUsernamesShow(!SharedConfig.fakeUsernamesShow);
                tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                        currentAccount, getUserConfig().getClientUserId());
                if (listView != null) listView.adapter.update(true);
                break;
            case ITEM_FAKE_USERNAMES_HIDE:
                showFakeUsernamesHideDialog();
                break;
            case ITEM_FAKE_PHONE:
                showFakePhoneDialog();
                break;
            case ITEM_FAKE_CHANNEL_ENABLE:
                SharedConfig.setFakePersonalChannelEnabled(!SharedConfig.fakePersonalChannelEnabled);
                if (listView != null) listView.adapter.update(true);
                break;
            case ITEM_FAKE_CHANNEL_LINK:
                showFakeChannelDialog();
                break;
            case ITEM_FAKE_CHANNEL_SUBS:
                showFakeChannelSubsDialog();
                break;
            case ITEM_FAKE_LEVEL:
                showFakeLevelDialog();
                break;
            case ITEM_FAKE_HOLD:
                SharedConfig.setFakeProfileHold(!SharedConfig.fakeProfileHold);
                tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
                tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                        currentAccount, getUserConfig().getClientUserId());
                if (listView != null) listView.adapter.update(true);
                break;
            case ITEM_FAKE_MAJOR:
                SharedConfig.setFakeProfileMajor(!SharedConfig.fakeProfileMajor);
                tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
                tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                        currentAccount, getUserConfig().getClientUserId());
                if (listView != null) listView.adapter.update(true);
                break;
            case ITEM_ADMIN_ADD:
                showAddLocalAdminChannelDialog();
                break;
            case ITEM_ADMIN_ROW:
                if (item.object instanceof tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.Overlay) {
                    showEditLocalAdminChannelDialog((tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.Overlay) item.object);
                }
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void refreshTestCards() {
        testCards = LarpPaymentHelper.getExampleTestCards(6);
    }

    private void showTestCardsDialog() {
        if (testCards.isEmpty()) {
            refreshTestCards();
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpTestCardsTitle));

        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        TextView hint = new TextView(getContext());
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray, resourceProvider));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setText(getString(R.string.LarpTestCardsInfo));
        container.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 12));

        for (String card : testCards) {
            TextView row = new TextView(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
                }
            };
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            row.setText(LarpPaymentHelper.formatCardNumber(card));
            row.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
            row.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
            row.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("card", card));
                    BulletinFactory.of(this).createCopyBulletin(getString(R.string.LarpTestCardsCopied)).show();
                }
            });
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        b.setView(scrollView);
        b.setPositiveButton(getString(R.string.LarpTestCardsGenerate), (d, w) -> {
            refreshTestCards();
            if (listView != null) {
                listView.adapter.update(true);
            }
            showTestCardsDialog();
        });
        b.setNegativeButton(getString(R.string.Close), null);
        showDialog(b.create());
    }

    private void showLicenseDialog() {
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpLicenseTitle));
        EditTextBoldCursor input = createInput(
                getString(R.string.LarpLicenseKeyPlaceholder),
                SharedConfig.larpLicenseKey,
                "AYU-XXXX-KEY-0000",
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.LarpLicenseActivate), (d, w) -> {
            licenseStatus = getString(R.string.LarpLicenseChecking);
            if (listView != null) listView.adapter.update(true);
            LicenseManager.activate(input.getText().toString(), (ok, message) -> {
                licenseStatus = licenseStatusText(ok, message);
                if (listView != null) listView.adapter.update(true);
            });
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private String licenseStatusText(boolean ok, String code) {
        if (ok) {
            return getString(R.string.LarpLicenseActive);
        }
        switch (code) {
            case "invalid_key":
                return getString(R.string.LarpLicenseInvalid);
            case "hwid_mismatch":
                return getString(R.string.LarpLicenseHwidMismatch);
            case "ip_mismatch":
                return getString(R.string.LarpLicenseIpMismatch);
            case "not_activated":
                return getString(R.string.LarpLicenseNotActivated);
            case "network_error":
                // Keep previous active status; just show temporary error text.
                if (LicenseManager.isActive()) {
                    return getString(R.string.LarpLicenseActive);
                }
                return getString(R.string.LarpLicenseNetworkError);
            default:
                return getString(R.string.LarpLicenseError);
        }
    }

    private void showFakeStarsDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeStarsTitle));
        EditTextBoldCursor input = createInput(getString(R.string.LarpFakeStarsHint),
                SharedConfig.fakeStarsAmount > 0 ? String.valueOf(SharedConfig.fakeStarsAmount) : "",
                "0",
                android.text.InputType.TYPE_CLASS_NUMBER);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakeStarsAmount(Math.max(0, Utilities.parseLong(input.getText().toString())));
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakeStarsAmount(0);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakeTonDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeTonTitle));
        EditTextBoldCursor input = createInput(getString(R.string.LarpFakeTonHint),
                SharedConfig.fakeTonAmount > 0 ? String.valueOf(SharedConfig.fakeTonAmount) : "",
                "0",
                android.text.InputType.TYPE_CLASS_NUMBER);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakeTonAmount(Math.max(0, Utilities.parseLong(input.getText().toString())));
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakeTonAmount(0);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakeNftsDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeNftsTitle));
        EditTextBoldCursor input = createInput(getString(R.string.LarpFakeNftsHint), SharedConfig.fakeNftSlug, "https://t.me/nft/...", android.text.InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(false);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakeNftSlug(extractFakeNftSlugs(input.getText().toString()));
            StarsController.getInstance(currentAccount).invalidateProfileGifts(getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakeNftSlug("");
            StarsController.getInstance(currentAccount).invalidateProfileGifts(getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakeUsernamesDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeUsernamesTitle));
        EditTextBoldCursor input = createMultilineInput(getString(R.string.LarpFakeUsernamesHint), SharedConfig.fakeFragmentUsernames, "username1, username2\nor one per line");
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakeFragmentUsernames(input.getText().toString());
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.applySelfUsernames(currentAccount);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                    currentAccount, getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakeFragmentUsernames("");
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.applySelfUsernames(currentAccount);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakeUsernamesHideDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeUsernamesHideTitle));
        EditTextBoldCursor input = createMultilineInput(getString(R.string.LarpFakeUsernamesHideHint), SharedConfig.fakeDisabledUsernames, "username1, username2\nor one per line");
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakeDisabledUsernames(input.getText().toString());
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.applySelfUsernames(currentAccount);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                    currentAccount, getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakeDisabledUsernames("");
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.applySelfUsernames(currentAccount);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                    currentAccount, getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakeChannelDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeChannelTitle));
        EditTextBoldCursor input = createInput(getString(R.string.LarpFakeChannelHint), SharedConfig.fakePersonalChannelLink, "https://t.me/channel or @channel", android.text.InputType.TYPE_CLASS_TEXT);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakePersonalChannelEnabled(true);
            SharedConfig.setFakePersonalChannelLink(input.getText().toString());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakePersonalChannelLink("");
            SharedConfig.setFakePersonalChannelEnabled(false);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakeChannelSubsDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeChannelSubsTitle));
        EditTextBoldCursor input = createInput(
                getString(R.string.LarpFakeChannelSubsHint),
                SharedConfig.fakePersonalChannelSubscribers > 0 ? String.valueOf(SharedConfig.fakePersonalChannelSubscribers) : "",
                "1000",
                android.text.InputType.TYPE_CLASS_NUMBER);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakePersonalChannelSubscribers((int) Utilities.parseInt(input.getText().toString()));
            tw.nekomimi.nekogram.helpers.LarpPersonalChannel.apply(currentAccount);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakePersonalChannelSubscribers(0);
            tw.nekomimi.nekogram.helpers.LarpPersonalChannel.apply(currentAccount);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFakePhoneDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakePhoneTitle));
        EditTextBoldCursor input = createInput(getString(R.string.LarpFakePhoneHint), SharedConfig.fakePhoneNumber, "+888 8 824", android.text.InputType.TYPE_CLASS_PHONE);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakePhoneNumber(input.getText().toString());
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakePhoneNumber("");
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private EditTextBoldCursor createInput(String hintText, String value, String hint, int inputType) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(hintText);
        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));

        EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(value == null ? "" : value);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        editText.setHintText(hint);
        editText.setSingleLine(true);
        editText.setInputType(inputType);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, 0, dp(42), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));

        return editText;
    }

    /** Multiline field for comma/newline-separated usernames (AyuGram Desktop parity). */
    private EditTextBoldCursor createMultilineInput(String hintText, String value, String hint) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(hintText);
        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 5, 24, 12));

        EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(value == null ? "" : value);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        editText.setHintText(hint);
        editText.setSingleLine(false);
        editText.setMinLines(3);
        editText.setMaxLines(8);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        editText.setBackgroundDrawable(null);
        editText.setPadding(0, dp(4), dp(12), dp(4));
        editText.setMinimumHeight(dp(96));
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));

        ScrollView scroll = new ScrollView(getContext());
        scroll.addView(container);
        // Keep parent for setView((View) input.getParent()) — wrap so getParent is ScrollView's child chain
        // Existing callers expect editText.getParent() == container. Preserve that.
        return editText;
    }

    private void showFakeLevelDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpFakeLevelTitle));
        EditTextBoldCursor input = createInput(
                getString(R.string.LarpFakeLevelHint),
                SharedConfig.fakeProfileLevel > 0 ? String.valueOf(SharedConfig.fakeProfileLevel) : "",
                "0",
                android.text.InputType.TYPE_CLASS_NUMBER);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            SharedConfig.setFakeProfileLevel((int) Utilities.parseInt(input.getText().toString()));
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                    currentAccount, getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(getString(R.string.SlowmodeOff), (d, w) -> {
            SharedConfig.setFakeProfileLevel(0);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.publishSelf(currentAccount);
            tw.nekomimi.nekogram.helpers.LarpProfileRelay.notifyProfileRefresh(
                    currentAccount, getUserConfig().getClientUserId());
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showAddLocalAdminChannelDialog() {
        if (!LicenseManager.isActive()) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpLocalAdminAdd));
        EditTextBoldCursor input = createInput(getString(R.string.LarpLocalAdminAddHint), "", "https://t.me/channel or @channel", android.text.InputType.TYPE_CLASS_TEXT);
        b.setView((View) input.getParent());
        b.setPositiveButton(getString(R.string.Add), (d, w) -> {
            tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.resolveAndAdd(currentAccount, input.getText().toString(), (ok, overlay, message) -> {
                if (ok) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.LarpLocalAdminAdded)).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.LarpLocalAdminAddFailed)).show();
                }
                if (listView != null) listView.adapter.update(true);
            });
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showEditLocalAdminChannelDialog(tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.Overlay source) {
        if (!LicenseManager.isActive() || source == null) return;
        final tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.Overlay o = source.copy();

        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        EditTextBoldCursor titleInput = createInput(getString(R.string.LarpLocalAdminTitleHint), o.title, "Channel title", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView((View) titleInput.getParent());
        EditTextBoldCursor usernameInput = createInput(getString(R.string.LarpLocalAdminUsernameHint), o.username, "channel_username", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView((View) usernameInput.getParent());
        EditTextBoldCursor subsInput = createInput(getString(R.string.LarpLocalAdminSubsHint), o.participantsCount > 0 ? String.valueOf(o.participantsCount) : "", "100000", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView((View) subsInput.getParent());
        EditTextBoldCursor adminsInput = createInput(getString(R.string.LarpLocalAdminAdminsHint), o.fakeAdmins, "@user1, @user2", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView((View) adminsInput.getParent());
        EditTextBoldCursor photoInput = createInput(getString(R.string.LarpLocalAdminPhotoHint), o.photoPath, "/storage/.../photo.jpg", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView((View) photoInput.getParent());

        TextView addAdminBtn = new TextView(getContext());
        addAdminBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider));
        addAdminBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addAdminBtn.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        addAdminBtn.setText(getString(R.string.LarpLocalAdminAddOne));
        addAdminBtn.setOnClickListener(v -> showAddOneAdminDialog(o.chatId));
        container.addView(addAdminBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(!TextUtils.isEmpty(o.link) ? o.link : ("id:" + o.chatId));
        b.setView(scrollView);
        b.setPositiveButton(getString(R.string.Done), (d, w) -> {
            o.title = titleInput.getText().toString().trim();
            o.username = usernameInput.getText().toString().trim();
            if (o.username.startsWith("@")) {
                o.username = o.username.substring(1);
            }
            o.participantsCount = Math.max(0, Utilities.parseInt(subsInput.getText().toString()));
            o.fakeAdmins = adminsInput.getText().toString().trim();
            o.photoPath = photoInput.getText().toString().trim();
            o.enabled = true;
            tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.put(o);
            tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.resolveFakeAdmins(currentAccount, o.chatId, () -> {
                if (listView != null) listView.adapter.update(true);
            });
            if (listView != null) listView.adapter.update(true);
        });
        b.setNeutralButton(o.enabled ? getString(R.string.Disable) : getString(R.string.Enable), (d, w) -> {
            o.enabled = !o.enabled;
            tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.put(o);
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Delete), (d, w) -> {
            tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.remove(o.chatId);
            if (listView != null) listView.adapter.update(true);
        });
        showDialog(b.create());
    }

    private void showAddOneAdminDialog(long chatId) {
        if (!LicenseManager.isActive() || chatId == 0) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(getContext(), resourceProvider);
        b.setTitle(getString(R.string.LarpLocalAdminAddOne));

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor input = createInput(getString(R.string.LarpLocalAdminAddOneHint), "", "@username or user id", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView((View) input.getParent());

        final org.telegram.ui.Components.BackupImageView avatar = new org.telegram.ui.Components.BackupImageView(getContext());
        avatar.setRoundRadius(AndroidUtilities.dp(24));
        avatar.setVisibility(View.GONE);
        container.addView(avatar, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 4));

        final TextView preview = new TextView(getContext());
        preview.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        preview.setGravity(Gravity.CENTER_HORIZONTAL);
        preview.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        preview.setText("");
        container.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final org.telegram.tgnet.TLRPC.User[] resolvedUser = new org.telegram.tgnet.TLRPC.User[1];
        final Runnable resolvePreview = () -> {
            String raw = input.getText().toString().trim();
            if (raw.startsWith("@")) raw = raw.substring(1);
            if (TextUtils.isEmpty(raw)) {
                resolvedUser[0] = null;
                avatar.setVisibility(View.GONE);
                preview.setText("");
                return;
            }
            long asId = 0;
            try { asId = Long.parseLong(raw); } catch (Exception ignored) {}
            if (asId != 0) {
                org.telegram.tgnet.TLRPC.User user = getMessagesController().getUser(asId);
                if (user == null) {
                    user = new org.telegram.tgnet.TLRPC.TL_user();
                    user.id = asId;
                    user.first_name = "User " + asId;
                    getMessagesController().putUser(user, false);
                }
                resolvedUser[0] = user;
                avatar.setVisibility(View.VISIBLE);
                avatar.setForUserOrChat(user, new org.telegram.ui.Components.AvatarDrawable(user));
                preview.setText(org.telegram.messenger.UserObject.getUserName(user)
                        + (TextUtils.isEmpty(user.username) ? "" : ("\n@" + user.username)));
                return;
            }
            org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername req = new org.telegram.tgnet.TLRPC.TL_contacts_resolveUsername();
            req.username = raw;
            preview.setText("…");
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!(response instanceof org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer)) {
                    resolvedUser[0] = null;
                    avatar.setVisibility(View.GONE);
                    preview.setText(getString(R.string.LarpLocalAdminAddFailed));
                    return;
                }
                org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer resolved = (org.telegram.tgnet.TLRPC.TL_contacts_resolvedPeer) response;
                getMessagesController().putUsers(resolved.users, false);
                getMessagesController().putChats(resolved.chats, false);
                org.telegram.tgnet.TLRPC.User user = null;
                if (resolved.peer instanceof org.telegram.tgnet.TLRPC.TL_peerUser) {
                    user = getMessagesController().getUser(((org.telegram.tgnet.TLRPC.TL_peerUser) resolved.peer).user_id);
                } else if (resolved.users != null && !resolved.users.isEmpty()) {
                    user = resolved.users.get(0);
                }
                resolvedUser[0] = user;
                if (user == null) {
                    avatar.setVisibility(View.GONE);
                    preview.setText(getString(R.string.LarpLocalAdminAddFailed));
                    return;
                }
                avatar.setVisibility(View.VISIBLE);
                avatar.setForUserOrChat(user, new org.telegram.ui.Components.AvatarDrawable(user));
                preview.setText(org.telegram.messenger.UserObject.getUserName(user)
                        + (TextUtils.isEmpty(user.username) ? "" : ("\n@" + user.username)));
            }));
        };

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                AndroidUtilities.cancelRunOnUIThread(resolvePreview);
                AndroidUtilities.runOnUIThread(resolvePreview, 350);
            }
        });

        b.setView(container);
        b.setPositiveButton(getString(R.string.Add), (d, w) -> {
            org.telegram.tgnet.TLRPC.User user = resolvedUser[0];
            if (user == null) {
                resolvePreview.run();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.LarpLocalAdminAddFailed)).show();
                return;
            }
            tw.nekomimi.nekogram.helpers.LarpChannelAdminOverlay.addFakeAdmin(chatId, user);
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.contact_check,
                    org.telegram.messenger.UserObject.getUserName(user) + " — " + getString(R.string.LarpLocalAdminAddedAdmin)
            ).show();
            if (listView != null) listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private static String extractFakeNftSlugs(String text) {
        if (text == null) return "";
        ArrayList<String> slugs = new ArrayList<>();
        String[] parts = text.split("[,\\n\\r]+");
        for (String part : parts) {
            String t = part == null ? "" : part.trim();
            if (t.isEmpty()) continue;
            int idx = t.indexOf("/nft/");
            if (idx >= 0) {
                t = t.substring(idx + "/nft/".length());
            } else if (t.startsWith("https://t.me/")) {
                int lastSlash = t.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash + 1 < t.length()) {
                    t = t.substring(lastSlash + 1);
                }
            }
            int q = t.indexOf('?');
            if (q >= 0) t = t.substring(0, q);
            int h = t.indexOf('#');
            if (h >= 0) t = t.substring(0, h);
            t = t.trim();
            if (!TextUtils.isEmpty(t)) {
                slugs.add(t);
            }
        }
        return TextUtils.join(",", slugs);
    }
}

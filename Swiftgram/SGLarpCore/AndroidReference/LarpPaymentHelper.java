package tw.nekomimi.nekogram.helpers;

import com.stripe.android.model.Card;

import org.json.JSONObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * [MG] Accept Stripe-style test cards for LARP invoice payments (stars top-up, premium, etc.).
 */
public final class LarpPaymentHelper {

    public static final String TEST_PAYMENT_JSON = "{\"type\":\"card\", \"id\":\"mg-larp-test\"}";

    /** Known test BIN prefixes — middle digits may be random if Luhn check passes. */
    private static final String[] TEST_BINS = {
            "424242", "400000", "400005", "403550", "411111", "401288",
            "555555", "520082", "510510", "222300", "222100", "222240",
            "601111", "601100", "601198",
            "371449", "378282", "378734",
            "305693", "362272", "356600",
    };

    private LarpPaymentHelper() {
    }

    public static boolean isLarpPaymentEnabled() {
        return true;
    }

    public static boolean isStarsInvoice(TLRPC.InputInvoice invoice) {
        return invoice instanceof TLRPC.TL_inputInvoiceStars;
    }

    public static boolean shouldSkipCustomerInfo(TLRPC.InputInvoice invoice) {
        return isLarpPaymentEnabled() && isStarsInvoice(invoice);
    }

    /** Strip customer/shipping/cardholder requirements for local stars checkout. */
    public static void preparePaymentForm(TLRPC.PaymentForm form, TLRPC.InputInvoice invoice) {
        if (!shouldSkipCustomerInfo(invoice) || form == null) {
            return;
        }
        if (form.invoice != null) {
            form.invoice.name_requested = false;
            form.invoice.phone_requested = false;
            form.invoice.email_requested = false;
            form.invoice.shipping_address_requested = false;
            form.invoice.flexible = false;
            form.invoice.email_to_provider = false;
            form.invoice.phone_to_provider = false;
        }
        if (form.native_params != null && form.native_params.data != null) {
            try {
                JSONObject json = new JSONObject(form.native_params.data);
                json.put("need_cardholder_name", false);
                json.put("need_country", false);
                json.put("need_zip", false);
                form.native_params.data = json.toString();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isLarpTestPaymentJson(String paymentJson) {
        return paymentJson != null && paymentJson.contains("mg-larp-test");
    }

    public static boolean matchesTestCardInput(String cardNumber, String expiry, String cvv) {
        if (!isLarpPaymentEnabled()) {
            return false;
        }
        if (!isTestCardNumber(cardNumber)) {
            return false;
        }
        if (!matchesCvv(cvv)) {
            return false;
        }
        return matchesExpiry(expiry, false);
    }

    public static boolean matchesTestCardNumber(String cardNumber) {
        if (!isLarpPaymentEnabled()) {
            return false;
        }
        return isTestCardNumber(cardNumber);
    }

    public static boolean isTestCardNumber(String cardNumber) {
        final String n = normalizeCardNumber(cardNumber);
        if (n.isEmpty() || !n.matches("\\d+")) {
            return false;
        }
        final int len = n.length();
        if (len != 15 && len != 16) {
            return false;
        }
        if (!passesLuhn(n)) {
            return false;
        }
        for (String bin : TEST_BINS) {
            if (n.startsWith(bin)) {
                return true;
            }
        }
        return len == 16 && n.endsWith("1111");
    }

    public static boolean matchesTestCardLoose(String cardNumber, String expiry, String cvv) {
        if (!isLarpPaymentEnabled()) {
            return false;
        }
        if (!isTestCardNumber(cardNumber)) {
            return false;
        }
        if (!matchesCvv(cvv)) {
            return false;
        }
        return matchesExpiry(expiry, true);
    }

    public static boolean isTestCard(Card card) {
        if (card == null) {
            return false;
        }
        return matchesTestCardInput(card.getNumber(), formatExpiry(card), card.getCVC());
    }

    /** Generate a random Luhn-valid test card from a random BIN. */
    public static String generateRandomTestCard() {
        final String bin = TEST_BINS[Utilities.fastRandom.nextInt(TEST_BINS.length)];
        final int targetLen = isAmexBin(bin) ? 15 : 16;
        final StringBuilder sb = new StringBuilder(bin);
        while (sb.length() < targetLen - 1) {
            sb.append(Utilities.fastRandom.nextInt(10));
        }
        sb.append(computeLuhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    /** Fixed examples plus freshly generated random cards for the settings UI. */
    public static ArrayList<String> getExampleTestCards(int randomCount) {
        final ArrayList<String> cards = new ArrayList<>();
        cards.add("4242424242424242");
        cards.add("5555555555554444");
        cards.add("4111111111111111");
        cards.add("4000056655665556");
        cards.add("2223000048400011");
        cards.add("5200828282828210");
        cards.add("6011111111111117");
        cards.add("378282246310005");
        for (int i = 0; i < randomCount; i++) {
            cards.add(generateRandomTestCard());
        }
        return cards;
    }

    public static String formatCardNumber(String raw) {
        final String n = normalizeCardNumber(raw);
        if (n.length() == 15) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n.length(); i++) {
                if (i == 4 || i == 10) {
                    sb.append(' ');
                }
                sb.append(n.charAt(i));
            }
            return sb.toString();
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(n.charAt(i));
        }
        return sb.toString();
    }

    public static void creditStarsPurchase(int currentAccount, long stars) {
        if (stars <= 0) {
            return;
        }
        SharedConfig.setFakeStarsAmount(LarpBalanceHelper.getStarsBalance() + stars);
        LarpBalanceHelper.notifyBalanceChanged(currentAccount, false);
        LarpTransactionHelper.addStarsTopup(currentAccount, stars);
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.starBalanceUpdated);
    }

    private static boolean isAmexBin(String bin) {
        return bin.startsWith("34") || bin.startsWith("37");
    }

    private static boolean matchesCvv(String cvv) {
        final String code = cvv == null ? "" : cvv.trim();
        if (code.isEmpty()) {
            return true;
        }
        return code.matches("\\d{3,4}");
    }

    private static boolean matchesExpiry(String expiry, boolean loose) {
        if (expiry == null || expiry.trim().isEmpty()) {
            return loose;
        }
        final int month = parseExpiryMonth(expiry);
        final int year = parseExpiryYear(expiry);
        if (month < 1 || month > 12) {
            return loose && year >= 2025;
        }
        return year >= 2025;
    }

    private static int computeLuhnCheckDigit(String partial) {
        int sum = 0;
        boolean alt = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int n = partial.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static boolean passesLuhn(String number) {
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private static String formatExpiry(Card card) {
        final Integer month = card.getExpMonth();
        final Integer year = card.getExpYear();
        if (month == null || year == null) {
            return "";
        }
        final int fullYear = year < 100 ? 2000 + year : year;
        return month + "/" + fullYear;
    }

    private static String[] parseExpiryParts(String expiry) {
        if (expiry == null) {
            return null;
        }
        String normalized = expiry.trim().replace(" ", "");
        String[] parts = normalized.split("[/.\\-]");
        if (parts.length == 2) {
            return parts;
        }
        if (normalized.length() == 4 && normalized.matches("\\d{4}")) {
            return new String[] { normalized.substring(0, 2), normalized.substring(2) };
        }
        return null;
    }

    private static int parseExpiryMonth(String expiry) {
        final String[] parts = parseExpiryParts(expiry);
        if (parts == null) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseExpiryYear(String expiry) {
        final String[] parts = parseExpiryParts(expiry);
        if (parts == null) {
            return -1;
        }
        try {
            int year = Integer.parseInt(parts[1].trim());
            if (year < 100) {
                year += 2000;
            }
            return year;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String normalizeCardNumber(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(" ", "").replace("-", "");
    }
}

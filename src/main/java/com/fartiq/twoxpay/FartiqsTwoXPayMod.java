package com.fartiq.twoxpay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fartiqs 2x Pay Mod.
 *
 * When DonutSMP prints "<name> paid you $<amount>", a gold [2x $<double>] button is
 * added to the end of that chat line. Clicking it runs: /pay <name> <double the amount>.
 * (Chat has to be open to click it - press T.)
 */
public class FartiqsTwoXPayMod implements ClientModInitializer {

    // Runs on cleaned text: every run of odd characters (the green $ icon, special spaces)
    // becomes one plain space. The name has to be the very first word on the line, so a
    // player typing "paid you $1b" in public chat can't create a button.
    private static final Pattern PAID_YOU = Pattern.compile(
            "^([A-Za-z0-9_.]+) paid you ([\\d,]*\\.?\\d+) ?([kmbt])?(?![A-Za-z])",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay) return message; // action bar text can't be clicked

            String raw = message.getString();
            // regular player chat has a separator like "Name » msg" or "Name: msg" - never touch those
            if (raw.contains(":") || raw.contains("\u00BB") || raw.contains("<")) return message;

            String clean = raw.replaceAll("[^A-Za-z0-9_.,]+", " ").trim();
            Matcher m = PAID_YOU.matcher(clean);

            if (!m.find()) {
                // looks like a payment line but couldn't be read -> red marker so we can tell
                if (clean.toLowerCase(Locale.ROOT).contains(" paid you ")) {
                    return message.copy().append(Text.literal(" [2x?]").formatted(Formatting.RED));
                }
                return message;
            }

            String name = m.group(1);
            double amount = parseAmount(m.group(2), m.group(3));
            if (amount <= 0) return message;

            long pay = Math.round(amount * 2);
            String command = "pay " + name + " " + pay; // no leading slash in 1.21.5+

            Text button = Text.literal(" [2x $" + shorten(pay) + "]").setStyle(Style.EMPTY
                    .withColor(Formatting.GOLD)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to run /" + command))));

            return message.copy().append(button);
        });
    }

    private static double parseAmount(String num, String suffix) {
        double v;
        try {
            v = Double.parseDouble(num.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
        if (suffix != null) {
            switch (Character.toLowerCase(suffix.charAt(0))) {
                case 'k' -> v *= 1_000d;
                case 'm' -> v *= 1_000_000d;
                case 'b' -> v *= 1_000_000_000d;
                case 't' -> v *= 1_000_000_000_000d;
                default -> { }
            }
        }
        return v;
    }

    private static String shorten(double v) {
        if (v >= 1e12) return trim(v / 1e12) + "T";
        if (v >= 1e9) return trim(v / 1e9) + "B";
        if (v >= 1e6) return trim(v / 1e6) + "M";
        if (v >= 1e3) return trim(v / 1e3) + "K";
        return trim(v);
    }

    private static String trim(double d) {
        return String.format(Locale.ROOT, "%.2f", d).replaceAll("\\.?0+$", "");
    }
}

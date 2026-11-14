package com.gtnhplanner.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.ModularUI;
import com.gtnhplanner.ui.tutorial.Pointer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.eigenraven.lwjgl3ify.api.InputEvents;

/**
 * The game's text input, for one of the planner's own text boxes while it is being written in. With lwjgl3ify (the
 * pack's Java 17+ game) typed text comes only while text input is on, as text events of its own: the numpad's digits,
 * an IME's words. A key press then carries a character only for some keys, and that one comes again as text. So a box
 * turns text input {@link #on} when writing starts and {@link #off} when it ends, takes its text from
 * {@code ModernInteractable#onTextInput}, and types a key's character only when {@link #keyTypes} says so. Without
 * lwjgl3ify none of this does anything and the keys carry the text, as always.
 */
public final class TextInput {

    /** Whether lwjgl3ify is in the game, so text comes as text events. */
    private static boolean events;
    /** How many times the game has shown another screen: lwjgl3ify turns text input off then, whoever had it on. */
    private static int screens;

    /** The {@link #screens} count when this box turned text input on; -1 while it has not. */
    private int held = -1;

    /** Learns whether lwjgl3ify is in the game, and from then counts the screens shown. Once, as the planner starts. */
    public static void register() {
        if (!ModularUI.Mods.LWJGL3IFY.isLoaded()) return;
        events = true;
        MinecraftForge.EVENT_BUS.register(new Screens());
    }

    /** Turns text input on for this box (again, if a change of screen turned it off). */
    public void on() {
        if (held == screens) return;
        held = screens;
        if (events) InputEvents.beginTextInput();
    }

    /** Turns it off again, if this box turned it on and no change of screen has since. */
    public void off() {
        if (held < 0) return;
        final boolean live = held == screens;
        held = -1;
        if (live && events) InputEvents.endTextInput();
    }

    /**
     * For a key with a character, pressed while this box is written in: whether that character is typed. Without
     * lwjgl3ify, always. With it, not while text input is on, as the text comes as a text event too; but when a change
     * of screen turned it off (text input goes on again here, for the keys after), and for the tour's keys, which come
     * with no text event.
     */
    public boolean keyTypes() {
        if (!events || Pointer.typing) return true;
        if (held == screens) return false;
        if (held >= 0) on();
        return true;
    }

    /** What of {@code text} may be written in a box, as the chat takes it (no control characters, no §). */
    public static String printable(final String text) {
        final StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (ChatAllowedCharacters.isAllowedCharacter(c)) b.append(c);
        }
        return b.toString();
    }

    /** Counts the screens shown, as lwjgl3ify resets its text input: on every change of screen. */
    public static final class Screens {

        @SubscribeEvent
        public void onGuiOpen(final GuiOpenEvent event) {
            if (Minecraft.getMinecraft().currentScreen != event.gui) screens++;
        }
    }
}

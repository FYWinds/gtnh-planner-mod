package com.gtnhplanner.cloud.session.game;

import java.io.File;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import com.gtnhplanner.Tags;
import com.gtnhplanner.api.PlanAPI;
import com.gtnhplanner.cloud.map.CloudGame;
import com.gtnhplanner.cloud.map.game.GameCloud;
import com.gtnhplanner.cloud.net.LiveClient;
import com.gtnhplanner.cloud.net.LiveSocket;
import com.gtnhplanner.cloud.session.CloudSession;
import com.gtnhplanner.cloud.store.CloudCache;
import com.gtnhplanner.data.flowchart.Graph;
import com.gtnhplanner.data.flowchart.Plan;
import com.gtnhplanner.data.flowchart.balancer.Severity;
import com.gtnhplanner.library.Account;
import com.gtnhplanner.ui.BoardSession;
import com.gtnhplanner.ui.UiText;
import com.gtnhplanner.ui.world.PlanSnapshot;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Cloud plans in the game: while an account is signed in the planner's plans are its cloud plans
 * ({@link CloudSession}), started the first time the planner needs them and stopped on sign-out; signed out, they are
 * the world's save, exactly as before. While a game server's team backend is attached
 * ({@link com.gtnhplanner.api.TeamBackend}) they are the team's plans on that server instead, kept apart on this
 * device. The session runs every client tick and is written to disk as the game closes.
 */
public final class CloudHooks {

    private CloudHooks() {}

    /** Wires it in, once, from the client proxy. */
    public static void init() {
        Plan.setCloudSource(CloudHooks::plans);
        // On the client thread, whatever thread the change came from (a team backend may come from the network's).
        Account.onChange(
            () -> Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    try {
                        accountChanged();
                    } catch (final RuntimeException e) {
                        com.gtnhplanner.GtnhPlanner.LOG.error("The planner could not switch its plans over", e);
                    }
                }));
        FMLCommonHandler.instance()
            .bus()
            .register(new CloudHooks.Ticks());
        Runtime.getRuntime()
            .addShutdownHook(new Thread(() -> {
                final CloudSession s = CloudSession.current();
                if (s != null) s.flush();
                LiveSocket.shutdown();
            }, "GTNH Planner cloud on exit"));
    }

    /**
     * The signed-in account's plans, or null (the world's save is opened then) when signed out, or while the account's
     * site is not known to have cloud plans: that is asked in the background, and the plans load again once it has.
     * Client thread.
     */
    @Nullable
    private static Plan plans() {
        final CloudSession s = session();
        if (s == null) return null;
        if (!s.ready()) {
            s.checkReady(Plan::reload);
            return null;
        }
        return s.load();
    }

    /**
     * The session of whoever cloud plans are for (the account signed in, or the player on an attached team backend),
     * started now if it is not yet; null when there is no one.
     */
    @Nullable
    public static CloudSession session() {
        return session(Minecraft.getMinecraft().mcDataDir, new GameHost());
    }

    /**
     * As {@link #session()}, with this device's copies under {@code gameDir} and {@code host} for a session it starts.
     * A team backend's session asks only that backend's session of the same server and player (the newest): once
     * another is attached, or none, it has none, and nothing of it goes anywhere.
     */
    @Nullable
    public static CloudSession session(final File gameDir, final CloudSession.Host host) {
        final String id = Account.accountId();
        final File folder = folder(gameDir);
        if (id == null || folder == null) return null;
        final CloudSession s = CloudSession.current();
        if (s != null && s.folder()
            .equals(folder)) return s;
        final Account.Team team = Account.team();
        final Supplier<String> token = team == null ? Account::token : () -> {
            final Account.Team now = Account.team();
            return team.sameScope(now) ? now.token() : null;
        };
        com.gtnhplanner.GtnhPlanner.LOG
            .info("[cloud] {}: plans kept in {}{}", id, folder, team != null ? " (the server's team)" : "");
        return CloudSession.start(id, token, new CloudCache(folder), team != null, host);
    }

    /**
     * Where this device keeps the cloud plans of whoever they are for now: the team backend's server and player apart
     * from every other, else the account's; null when there is no one.
     */
    @Nullable
    public static File folder(final File gameDir) {
        final Account.Team team = Account.team();
        if (team != null) return CloudCache.teamDir(gameDir, team.serverId(), team.userId());
        final String id = Account.accountId();
        return id == null ? null : CloudCache.accountDir(gameDir, id);
    }

    /**
     * Signed in or out, or a team backend attached or detached: the plans in hand are saved where they belong, and come
     * from where they now do.
     */
    private static void accountChanged() {
        final CloudSession s = CloudSession.current();
        final File folder = folder(Minecraft.getMinecraft().mcDataDir);
        Plan.reload();
        if (s != null && !s.folder()
            .equals(folder)) s.stop();
    }

    /** The session's tick, every client tick: network answers, edits out and in. */
    public static final class Ticks {

        @SubscribeEvent
        public void onTick(final TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            loadTeamPlansEarly();
            final CloudSession s = CloudSession.current();
            if (s == null) return;
            try {
                s.tick();
            } catch (final RuntimeException e) {
                com.gtnhplanner.GtnhPlanner.LOG.error("The cloud plans' tick failed", e);
            }
        }
    }

    /**
     * On a team backend the plans load as soon as the world is up and NEI has its recipes, not at the first open:
     * reading every plan of the team freezes the game (a second at 250 cards), and nobody is looking at the planner
     * yet while the world comes in.
     */
    private static void loadTeamPlansEarly() {
        if (Plan.loaded() != null || Account.team() == null) return;
        if (Minecraft.getMinecraft().theWorld == null || !codechicken.nei.NEIClientConfig.isLoaded()) return;
        Plan.getInstance();
    }

    /** The game as the session sees it. */
    private static final class GameHost implements CloudSession.Host {

        @Override
        public CloudGame game() {
            return new GameCloud();
        }

        @Override
        public LiveClient.Hello hello() {
            final Minecraft mc = Minecraft.getMinecraft();
            final String world = GameCloud.worldKey();
            return new LiveClient.Hello(
                Tags.VERSION,
                world.isEmpty() ? null : LiveClient.serverHash(world),
                mc.getSession() == null ? null
                    : mc.getSession()
                        .getUsername());
        }

        @Override
        public boolean busy(final Graph graph) {
            final BoardSession board = BoardSession.current();
            return board != null && board.busyWith(graph);
        }

        @Override
        public void replaced(final Graph old, final Graph next) {
            PlanSnapshot.regraph(old, next);
        }

        @Override
        @Nullable
        public Plan worldPlans() {
            return Plan.loadLocal();
        }

        @Override
        public boolean backUpWorld() {
            return PlanAPI.backUpNow("-before-upload");
        }

        @Override
        public boolean saveWorld(final Plan plans) {
            return PlanAPI.saveLocal(plans);
        }

        @Override
        public void shared(final String ownerName, final String planName, final String key) {
            final String text = UiText.tr("cloud.shared_with_you", ownerName, planName);
            final BoardSession board = BoardSession.current();
            if (board != null)
                board.flash(Severity.INFO, text, UiText.tr("cloud.open"), () -> board.openPlanByKey(key), 30);
            else if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer
                .addChatMessage(com.gtnhplanner.client.OpenPlanCommand.line(text, key));
        }

        @Override
        public void notice(final String text) {
            final BoardSession board = BoardSession.current();
            if (board != null) board.flash(Severity.INFO, text);
            else if (Minecraft.getMinecraft().thePlayer != null)
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(text));
        }
    }
}

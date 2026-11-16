package com.gtnhplanner.library;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import com.gtnhplanner.GtnhPlanner;

/**
 * The player's gtnhplanner.com account in this game: who is signed in and the session the site gave them, kept in
 * the config folder so it lasts as long as the website's own sign-in does (180 days). The password is never kept.
 * Also this game's device id, which the site asks for with every post.
 * <p>
 * While a game server's team backend is attached ({@link #team()}), cloud plans are the team's and the player is who
 * that server says: {@link #accountId()}, {@link #userId()} and {@link #username()} answer for it. {@link #token()},
 * {@link #signedIn()} and the file stay the website's, for the community library and posting.
 */
public final class Account {

    private static final String SESSION = "session", USERNAME = "username", DEVICE = "device";

    private static Properties props;

    private Account() {}

    /** The session the site gave ({@code gtnh_session}), sent with every signed-in call; null when signed out. */
    @Nullable
    public static String token() {
        return load().getProperty(SESSION);
    }

    /**
     * Who cloud plans are for: the player on the attached team backend ({@link #team()}), else the signed-in
     * account's id as the session names it ({@code <id>.<expires>.<signature>}): known offline, so the account's plans
     * open without the site. Null when signed out with no team backend.
     */
    @Nullable
    public static String accountId() {
        final Team on = team;
        if (on != null) return on.userId();
        final String t = token();
        final int dot = t == null ? -1 : t.indexOf('.');
        return dot <= 0 ? null : t.substring(0, dot);
    }

    /** Who the player is: their name on the attached team backend, else who is signed in, or null. */
    @Nullable
    public static String username() {
        final Team on = team;
        if (on != null) return on.username();
        return token() == null ? null : load().getProperty(USERNAME);
    }

    public static boolean signedIn() {
        return token() != null;
    }

    /** {@link #accountId()}: the id people are known by on cloud plans (a task's people, a comment's author). */
    @Nullable
    public static String userId() {
        return accountId();
    }

    /**
     * Hears every change of who cloud plans are for (their plans come and go with it): a sign-in or sign-out, a team
     * backend attached or detached. Not a sign-in or sign-out while a team backend is attached: its plans stay.
     */
    @Nullable
    private static Runnable changed;

    public static void onChange(@Nullable final Runnable listener) {
        changed = listener;
    }

    static void signedIn(final CommunityApi.SignedIn s) {
        load().setProperty(SESSION, s.token());
        load().setProperty(USERNAME, s.username());
        save();
        if (changed != null && team == null) changed.run();
    }

    /** Who signed in last, signed in now or not: the sign-in form starts from it. */
    @Nullable
    public static String lastUsername() {
        return load().getProperty(USERNAME);
    }

    /** Signs out; the name stays, for the next sign-in. */
    public static void signOut() {
        final boolean was = signedIn();
        load().remove(SESSION);
        save();
        if (was && changed != null && team == null) changed.run();
    }

    // region A team backend in the website's place

    /**
     * A game server's team backend that keeps cloud plans in the website's place while the player is on that server
     * ({@link com.gtnhplanner.api.TeamBackend}): the session it gave, the player there (their Minecraft UUID and
     * name), the server's plan store and the world's key. Kept in memory only, never in the account file.
     */
    public record Team(String token, String userId, String username, String serverId, String worldKey) {

        /** Whether {@code other} is the same server's store and the same player (its session may be newer). */
        public boolean sameScope(@Nullable final Team other) {
            return other != null && serverId.equals(other.serverId) && userId.equals(other.userId);
        }

        /** Without the session, so a log line never carries it. */
        @Override
        public String toString() {
            return "Team[" + username + " " + userId + " on " + serverId + "]";
        }
    }

    @Nullable
    private static volatile Team team;

    /** The team backend cloud plans go to now, or null for the website's account. */
    @Nullable
    public static Team team() {
        return team;
    }

    /**
     * Attaches a team backend, or detaches it ({@code null}). Another server or player than before changes who cloud
     * plans are for, heard as a sign-in is; the same one again only takes its new session. The website's sign-in is
     * left as it is and is the one again once detached.
     */
    public static void useTeam(@Nullable final Team next) {
        final Team was = team;
        team = next;
        final boolean moved = next == null ? was != null : !next.sameScope(was);
        if (moved && changed != null) changed.run();
    }

    // endregion

    /** This game's id for the site, made once. */
    static String deviceId() {
        String id = load().getProperty(DEVICE);
        if (id == null) {
            id = "gtnhplanner-" + UUID.randomUUID();
            load().setProperty(DEVICE, id);
            save();
        }
        return id;
    }

    /** The file a test keeps the account in instead of the game's config folder; null in the game. */
    @Nullable
    private static File testFile;

    /** Keeps the account in {@code f} from now on and forgets what was read (for tests). */
    static synchronized void useFile(final File f) {
        testFile = f;
        props = null;
    }

    private static File file() {
        if (testFile != null) return testFile;
        return new File(Minecraft.getMinecraft().mcDataDir, "config/gtnhplanner-account.properties");
    }

    private static synchronized Properties load() {
        if (props != null) return props;
        props = new Properties();
        // Read from the name before the rename until the new one has been written.
        File f = file();
        if (!f.isFile()) f = new File(f.getParentFile(), "plannh-account.properties");
        if (!f.isFile()) return props;
        try (Reader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (final IOException e) {
            GtnhPlanner.LOG.warn("Could not read the account file", e);
        }
        return props;
    }

    private static synchronized void save() {
        final File f = file(), dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        try (Writer out = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            props
                .store(out, "GTNH Planner: your library sign-in (a session, never your password). Delete to sign out.");
        } catch (final IOException e) {
            GtnhPlanner.LOG.warn("Could not write the account file", e);
        }
    }
}

package com.gtnhplanner.api;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.cloud.net.CloudApi;
import com.gtnhplanner.library.Account;

/**
 * A game server's team backend for cloud plans (docs/eto-fork.md, "Team mode"): while one is attached, the planner's
 * cloud plans, live editing and who the player is come from the server the player is on, scoped to their team, in the
 * website's place. The community library, feedback and the website listener stay on gtnhplanner.com with the website's
 * sign-in, which is left as it is and is the one again once detached. The server's session goes to that server only.
 * <p>
 * ETO GTNH Tweaks calls it by reflection when it is in the pack; the planner never needs Tweaks. Entering and leaving
 * team mode loads the plans again from where they now come from, as signing in and out does.
 */
public final class TeamBackend {

    private TeamBackend() {}

    /**
     * A server-side team backend took over cloud plans for this connection. Idempotent: called again with the same
     * serverId and userId it only replaces the token.
     *
     * @param site     REST base, e.g. "http://gtnh.u0u.io:25565/etotweaks/planner" (no trailing slash)
     * @param live     WebSocket address, e.g. "ws://gtnh.u0u.io:25565/etotweaks/planner/live"
     * @param token    the session token ({@code <userId>.<expiresEpochMillis>.<signature>})
     * @param userId   the player's UUID, lower case with dashes
     * @param username the player's Minecraft name
     * @param serverId stable id of the server's planner store (a UUID string); scopes the local cache
     * @param worldKey stable key of the world for task locations and placed machines; replaces the one the
     *                 Planner derives from the typed server address
     * @throws IllegalArgumentException when any of them is blank, or the addresses are not http(s) and ws(s)
     */
    public static void attach(final String site, final String live, final String token, final String userId,
        final String username, final String serverId, final String worldKey) {
        need("site", site);
        need("live", live);
        need("token", token);
        need("userId", userId);
        need("username", username);
        need("serverId", serverId);
        need("worldKey", worldKey);
        if (!site.startsWith("http://") && !site.startsWith("https://"))
            throw new IllegalArgumentException("site is not an http(s) address: " + site);
        if (!live.startsWith("ws://") && !live.startsWith("wss://"))
            throw new IllegalArgumentException("live is not a ws(s) address: " + live);
        // Where the session goes, before anything can use it.
        CloudApi.useTeam(token, site, live);
        final Account.Team next = new Account.Team(token, userId, username, serverId, worldKey);
        final boolean fresh = !next.sameScope(Account.team());
        Account.useTeam(next);
        // One line a call, never the session: the game's log shows the hand-over.
        GtnhPlanner.LOG.info(
            "[team] attached: site={} live={} userId={} username={} serverId={} ({})",
            site,
            live,
            userId,
            username,
            serverId,
            fresh ? "plans load from the team" : "same server and player, new session only");
    }

    /** Back to the website (or signed-out) behaviour; called on disconnect. Safe to call when not attached. */
    public static void detach() {
        final Account.Team was = Account.team();
        Account.useTeam(null);
        if (was == null) GtnhPlanner.LOG.info("[team] detached: none was attached");
        else GtnhPlanner.LOG.info(
            "[team] detached: userId={} username={} serverId={}; cloud plans are the website's again",
            was.userId(),
            was.username(),
            was.serverId());
    }

    private static void need(final String name, final String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
    }
}

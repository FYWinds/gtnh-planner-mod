package com.gtnhplanner.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnhplanner.api.TeamBackend;
import com.gtnhplanner.cloud.map.Boards;
import com.gtnhplanner.cloud.map.CloudGame;
import com.gtnhplanner.cloud.map.CloudPlans;
import com.gtnhplanner.cloud.map.TestGame;
import com.gtnhplanner.cloud.net.CloudApi;
import com.gtnhplanner.cloud.net.LiveClient;
import com.gtnhplanner.cloud.session.CloudSession;
import com.gtnhplanner.cloud.session.game.CloudHooks;
import com.gtnhplanner.cloud.sync.LiveOp;
import com.gtnhplanner.data.flowchart.Graph;
import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.data.flowchart.Plan;
import com.gtnhplanner.library.CommunityApi;
import com.gtnhplanner.library.PlanExport;
import com.gtnhplanner.ui.UiText;
import com.sun.net.httpserver.HttpServer;

/**
 * The planner against a game server's team backend, without the game: {@link LiveTestServer} mounted under
 * {@code /etotweaks/planner} on one port, as Tweaks serves it on the game's, with a session per player and the REST
 * routes beside the socket. The planner's own session ({@link CloudHooks#session(File, CloudSession.Host)}) after
 * {@link TeamBackend#attach}: the world's plans go up into the team, a plan made here is made there, a team mate (a
 * second session) edits it and the edit arrives, and the player's arrives at the team mate. Every request goes to the
 * backend's paths with the team's session; the website hears nothing.
 */
class TeamBackendLiveTest {

    private static final String P = "/etotweaks/planner";
    private static final String STEVE = "0f8fad5b-d9cb-469f-a165-70867728950e",
        ALEX = "7c9e6679-7425-40de-944b-e07fc1f90ae7", SERVER = "c3d4e5f6-0000-4000-8000-000000000001";
    private static final String STEVE_TOKEN = STEVE + ".1800000000000.5e55",
        STEVE_TOKEN_2 = STEVE + ".1800000000001.5e56", ALEX_TOKEN = ALEX + ".1800000000000.a1ec";

    @TempDir
    File dir;

    private LiveTestServer server;
    private HttpServer website;
    private final List<String> websiteHeard = new CopyOnWriteArrayList<>();
    private final String siteWas = CommunityApi.site();
    /** The team's plans as the backend lists them, by doc key ({@code <ownerId>:<designId>}). */
    private final Map<String, JsonObject> designs = new ConcurrentHashMap<>();
    private String site, live;
    private CloudSession session;
    private final Host host = new Host();
    private LiveClient alex;

    /** The game as the session sees it. */
    private static final class Host implements CloudSession.Host {

        final TestGame game = new TestGame();
        final List<String> notices = new CopyOnWriteArrayList<>();
        @Nullable
        Plan inHand, world;

        @Override
        public CloudGame game() {
            return game;
        }

        @Override
        public LiveClient.Hello hello() {
            return new LiveClient.Hello("test", LiveClient.serverHash("world:team-world"), "Steve");
        }

        @Override
        public boolean busy(final Graph graph) {
            return false;
        }

        @Override
        public void replaced(final Graph old, final Graph next) {}

        @Override
        public void notice(final String text) {
            notices.add(text);
        }

        final List<String> sharedWithMe = new CopyOnWriteArrayList<>();

        @Override
        public void shared(final String ownerName, final String planName, final String key) {
            sharedWithMe.add(key);
        }

        @Override
        @Nullable
        public Plan inHand() {
            return inHand;
        }

        @Override
        @Nullable
        public Plan worldPlans() {
            return world;
        }

        @Override
        public boolean backUpWorld() {
            return true;
        }

        @Override
        public boolean saveWorld(final Plan plans) {
            return true;
        }
    }

    @BeforeEach
    void start() throws Exception {
        server = new LiveTestServer(null);
        server.rooms = true;
        server.prefix = P;
        server.accounts.put(STEVE_TOKEN, new String[] { STEVE, "Steve" });
        server.accounts.put(STEVE_TOKEN_2, new String[] { STEVE, "Steve" });
        server.accounts.put(ALEX_TOKEN, new String[] { ALEX, "Alex" });
        server.rest = this::rest;
        site = "http://127.0.0.1:" + server.port() + P;
        live = "ws://127.0.0.1:" + server.port() + P + "/live";
        // The team mate's own game attached the same backend: their session goes there too.
        CloudApi.useTeam(ALEX_TOKEN, site, live);
        // The website: the team's plans must never reach it.
        website = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        website.createContext("/", ex -> {
            websiteHeard.add(ex.getRequestMethod() + " " + ex.getRequestURI());
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        website.start();
        CommunityApi.useSite(
            "http://127.0.0.1:" + website.getAddress()
                .getPort());
    }

    @AfterEach
    void stop() {
        if (alex != null) alex.stop();
        if (session != null) session.stop();
        TeamBackend.detach();
        server.close();
        website.stop(0);
        CommunityApi.useSite(siteWas);
    }

    // region The backend's REST side, as the contract has it

    private String[] rest(final String method, final String uri, final String[] who, final String body) {
        final int q = uri.indexOf('?');
        final String path = q < 0 ? uri : uri.substring(0, q);
        final Map<String, String> query = query(q < 0 ? "" : uri.substring(q + 1));
        final String me = who[0];
        if (method.equals("GET") && path.equals("/api/community/auth/me"))
            return ok("{\"user\":{\"id\":\"" + me + "\",\"username\":\"" + who[1] + "\"}}");
        if (method.equals("GET") && path.equals("/api/library")) {
            final JsonArray own = new JsonArray(), shared = new JsonArray();
            for (final Map.Entry<String, JsonObject> e : designs.entrySet()) {
                final boolean mine = e.getKey()
                    .startsWith(me + ":");
                if (mine) own.add(e.getValue());
                else if (!e.getValue()
                    .has("deletedAt")) shared.add(e.getValue());
            }
            final JsonObject o = new JsonObject();
            o.add("designs", own);
            o.add("shared", shared);
            return ok(o.toString());
        }
        if (method.equals("GET") && path.equals("/api/accounts/search")) return ok(
            "{\"accounts\":[{\"id\":\"" + ALEX
                + "\",\"username\":\"Alex\"},{\"id\":\""
                + STEVE
                + "\",\"username\":\"Steve\"}]}");
        if (method.equals("GET") && path.equals("/live/nearby"))
            return ok("{\"accounts\":[{\"id\":\"" + ALEX + "\",\"username\":\"Alex\",\"mcName\":\"Alex\"}]}");
        if (!path.startsWith("/api/library/designs/")) return new String[] { "404", "{\"error\":\"not found\"}" };
        final String rest = path.substring("/api/library/designs/".length());
        final String id = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        final String owner = query.getOrDefault("owner", me);
        final String key = owner + ":" + id;
        if (rest.contains("/members")) {
            if (!method.equals("GET")) return new String[] { "403", "{\"error\":\"membership is the team\"}" };
            final String other = owner.equals(STEVE) ? ALEX : STEVE;
            return ok(
                "{\"ownerId\":\"" + owner
                    + "\",\"ownerName\":\""
                    + name(owner)
                    + "\",\"role\":\""
                    + (owner.equals(me) ? "owner" : "editor")
                    + "\",\"members\":[{\"id\":\""
                    + other
                    + "\",\"username\":\""
                    + name(other)
                    + "\"}]}");
        }
        switch (method) {
            case "GET" -> {
                final JsonObject o = new JsonObject();
                o.add("design", designs.get(key));
                o.add("plan", server.plans.getOrDefault(key, new JsonObject()));
                o.addProperty("seq", server.seqs.getOrDefault(key, 0L));
                o.addProperty("role", owner.equals(me) ? "owner" : "editor");
                return ok(o.toString());
            }
            case "PUT" -> {
                final JsonObject in = new JsonParser().parse(body)
                    .getAsJsonObject();
                if (in.has("plan")) server.plans.put(key, in.getAsJsonObject("plan"));
                designs.put(
                    key,
                    design(
                        owner,
                        id,
                        in.get("name")
                            .getAsString()));
                return ok("{\"design\":" + designs.get(key) + ",\"behind\":false}");
            }
            case "DELETE" -> {
                final JsonObject d = designs.get(key);
                if (d != null) d.addProperty("deletedAt", "2026-11-15T12:00:00Z");
                return ok("{}");
            }
            default -> {
                return new String[] { "405", "{}" };
            }
        }
    }

    private static String[] ok(final String body) {
        return new String[] { "200", body };
    }

    private static String name(final String id) {
        return id.equals(STEVE) ? "Steve" : "Alex";
    }

    private static JsonObject design(final String owner, final String id, final String name) {
        final JsonObject d = new JsonObject();
        d.addProperty("ownerId", owner);
        d.addProperty("ownerName", name(owner));
        d.addProperty("id", id);
        d.addProperty("name", name);
        d.addProperty("closed", false);
        d.addProperty("favorite", false);
        d.addProperty("createdAt", "2026-11-15T10:00:00Z");
        d.addProperty("updatedAt", "2026-11-15T10:00:00Z");
        d.addProperty("planUpdatedAt", "2026-11-15T10:00:00Z");
        d.add("members", new JsonArray());
        return d;
    }

    private static Map<String, String> query(final String q) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final String part : q.split("&")) {
            if (part.isEmpty()) continue;
            final int eq = part.indexOf('=');
            out.put(
                eq < 0 ? part : part.substring(0, eq),
                eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    // endregion

    // region Helpers

    private void attach(final String token, final String serverId) {
        TeamBackend.attach(site, live, token, STEVE, "Steve", serverId, "world:team-world");
    }

    /** The planner's plans from the team, as the game opens them: the session, ready, loaded. */
    private Plan begin() {
        session = CloudHooks.session(dir, host);
        assertNotNull(session);
        final boolean[] ready = { false };
        session.checkReady(() -> ready[0] = true);
        tickUntil("the backend has cloud plans", () -> ready[0] || session.ready());
        final Plan p = session.load();
        host.inHand = p;
        return p;
    }

    private void tickUntil(final String what, final BooleanSupplier done) {
        final long until = System.currentTimeMillis() + 15_000;
        while (!done.getAsBoolean()) {
            if (System.currentTimeMillis() > until)
                throw new AssertionError("timed out: " + what + "; the backend heard " + server.requests);
            session.tick();
            if (alex != null) alex.poll();
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private void ticks(final int n) {
        for (int i = 0; i < n; i++) {
            session.tick();
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static Graph slot(final Plan p, final String key) {
        for (final Graph g : p.getGraphs()) if (key.equals(g.getCloudKey())) return g;
        return null;
    }

    private static Node onTheBoard(final Graph g) {
        for (final Node n : g.getNodes()) if (n.group == null) return n;
        return g.getNodes()
            .iterator()
            .next();
    }

    /** Whether the backend heard {@code method path} with {@code token} as the session. */
    private boolean heard(final String request, final String token) {
        return server.requests.contains(request + " " + token);
    }

    private static final class Heard implements LiveClient.Listener {

        final List<LiveClient.Snapshot> snapshots = new CopyOnWriteArrayList<>();
        final List<LiveClient.OpsBatch> ops = new CopyOnWriteArrayList<>();

        @Override
        public void onSnapshot(final LiveClient.Snapshot snapshot) {
            snapshots.add(snapshot);
        }

        @Override
        public void onOps(final LiveClient.OpsBatch batch) {
            ops.add(batch);
        }
    }

    // endregion

    @Test
    void theTeamEditsAPlanTogetherUnderTheBackendsPath() throws IOException {
        // The world's own plan, never sent anywhere: it goes up into the team once.
        final Plan world = Plan.cloud("unused");
        final Graph fromWorld = Boards.corpus("mv");
        host.game.knowing(fromWorld);
        fromWorld.setName("MV line");
        world.getGraphs()
            .add(fromWorld);
        host.world = world;

        attach(STEVE_TOKEN, SERVER);
        final Plan p = begin();
        assertEquals(new File(dir, "gtnhplanner/team/" + SERVER + "/" + STEVE), session.folder());
        assertTrue(
            fromWorld.getCloudKey()
                .startsWith(STEVE + ":"),
            "the world's plan is the player's: " + fromWorld.getCloudKey());
        tickUntil(
            "the world's plan is in the team",
            () -> host.notices.contains(UiText.tr("cloud.world_plans_in_team")));
        assertTrue(designs.containsKey(fromWorld.getCloudKey()));

        // A plan made here is made in the team.
        final Graph made = Boards.corpus("sulfuric-acid");
        host.game.knowing(made);
        made.setName("Acid");
        p.getGraphs()
            .add(made);
        session.save(p);
        final String key = made.getCloudKey();
        assertTrue(key.startsWith(STEVE + ":"));
        final String id = key.substring(STEVE.length() + 1);
        tickUntil("made in the team", () -> designs.containsKey(key) && server.plans.containsKey(key));

        // Open: in its room, read.
        p.setActiveIndex(
            p.getGraphs()
                .indexOf(slot(p, key)));
        session.save(p);
        tickUntil(
            "in the room",
            () -> session.doc(key) != null && session.doc(key)
                .inRoom()
                && !session.doc(key)
                    .behind());
        assertEquals(CloudSession.Status.LIVE, session.status());
        assertEquals(
            STEVE,
            session.client()
                .you()
                .id(),
            "the backend knows the session as the player");

        // A team mate, on a session of their own, joins and moves a card.
        alex = new LiveClient(URI.create(live), () -> ALEX_TOKEN, new LiveClient.Hello("test", null, "Alex"));
        final Heard heard = new Heard();
        alex.addListener(heard);
        alex.start();
        tickUntil("the team mate is in", alex::ready);
        assertEquals(
            ALEX,
            alex.you()
                .id());
        alex.join(key);
        tickUntil("the team mate has the plan", () -> !heard.snapshots.isEmpty());
        final Graph board = slot(p, key);
        final Node theirs = onTheBoard(board);
        final JsonObject at = new JsonObject(), pos = new JsonObject();
        pos.addProperty("x", 999);
        pos.addProperty("y", 333);
        at.add("position", pos);
        final JsonArray ops = new JsonArray();
        ops.add(
            LiveOp.patch("nodes", theirs.id.toString(), at, null)
                .toJson());
        assertTrue(
            alex.sendOps(
                key,
                heard.snapshots.get(0)
                    .seq(),
                "alex-1",
                ops));
        tickUntil("the team mate's edit arrives", () -> {
            final Graph now = slot(p, key);
            return now != null && now.nodes.get(theirs.id) != null
                && now.nodes.get(theirs.id).x == PlanExport.gameX(999);
        });

        // The player's edit arrives at the team mate, stamped as the player.
        final Graph now = slot(p, key);
        Node mine = null;
        for (final Node n : now.getNodes()) if (n.group == null && !n.id.equals(theirs.id)) mine = n;
        assertNotNull(mine);
        mine.x += 40;
        now.touchLayout();
        session.save(p);
        tickUntil(
            "the player's edit reaches the team mate",
            () -> heard.ops.stream()
                .anyMatch(
                    b -> b.by()
                        .equals(STEVE)
                        && b.ops()
                            .size() > 0));

        // The routes the planner asks for, exactly.
        assertEquals(
            "Steve",
            CloudApi.me(STEVE_TOKEN)
                .username());
        CloudApi.plan(STEVE_TOKEN, STEVE, id);
        assertEquals(
            ALEX,
            CloudApi.sharing(STEVE_TOKEN, id, null)
                .members()
                .get(0)
                .id());
        assertEquals(
            2,
            CloudApi.search(STEVE_TOKEN, "e")
                .size());
        assertEquals(
            ALEX,
            CloudApi.nearby(STEVE_TOKEN, List.of("Alex"))
                .get(0)
                .id());
        for (final String request : List.of(
            "GET " + P + "/live/health",
            "GET " + P + "/api/library",
            "PUT " + P + "/api/library/designs/" + id,
            "GET " + P + "/live",
            "GET " + P + "/api/community/auth/me",
            "GET " + P + "/api/library/designs/" + id + "?owner=" + STEVE,
            "GET " + P + "/api/library/designs/" + id + "/members",
            "GET " + P + "/api/accounts/search?q=e",
            "GET " + P + "/live/nearby?names=Alex"))
            assertTrue(heard(request, STEVE_TOKEN), request + " not heard: " + server.requests);
        assertTrue(heard("GET " + P + "/live", ALEX_TOKEN), "the team mate's socket");
        for (final String r : server.requests)
            assertTrue(r.endsWith(" " + STEVE_TOKEN) || r.endsWith(" " + ALEX_TOKEN), "with a team session: " + r);
        assertTrue(websiteHeard.isEmpty(), "the website heard: " + websiteHeard);
    }

    @Test
    void aTeamMatesPlanIsInThePlannerAndTheirsToDeleteForTheTeam() throws IOException {
        attach(STEVE_TOKEN, SERVER);
        final Plan p = begin();
        // The team mate makes a plan.
        final Graph g = Boards.dress(Boards.corpus("glass"));
        host.game.knowing(g);
        CloudApi.put(
            ALEX_TOKEN,
            "d2",
            new CloudApi.Upsert(
                "Glass",
                null,
                null,
                false,
                false,
                null,
                "2026-11-15T10:00:00.000Z",
                "2026-11-15T10:00:00.000Z",
                "2026-11-15T10:00:00.000Z",
                CloudPlans.write(g, host.game, null, null)
                    .plan()));
        final String key = ALEX + ":d2";
        assertTrue(designs.containsKey(key));
        session.refreshList();
        tickUntil("the team mate's plan is in the planner", () -> slot(p, key) != null);
        final Graph theirs = slot(p, key);
        assertFalse(session.shared(theirs), "the team's, not shared with the player");
        assertEquals("Alex", session.sharedBy(theirs), "who made it");

        // Deleting it deletes it for the team: the backend is told whose it is.
        session.deleting(theirs);
        p.removeSlot(
            p.getGraphs()
                .indexOf(theirs));
        session.save(p);
        tickUntil(
            "deleted for the team",
            () -> heard("DELETE " + P + "/api/library/designs/d2?owner=" + ALEX, STEVE_TOKEN));
        assertTrue(
            designs.get(key)
                .has("deletedAt"));
        assertFalse(
            server.requests.stream()
                .anyMatch(r -> r.contains("/members/")),
            "nobody leaves a team's plan");
        assertTrue(websiteHeard.isEmpty(), "the website heard: " + websiteHeard);
    }

    @Test
    void theTeamsPlansComeUnannouncedAndLeaveWithTheTeam() {
        attach(STEVE_TOKEN, SERVER);
        final Plan p = begin();
        final Graph made = Boards.corpus("glass");
        host.game.knowing(made);
        made.setName("Glass");
        p.getGraphs()
            .add(made);
        session.save(p);
        final String key = made.getCloudKey();
        tickUntil(
            "made, and live",
            () -> designs.containsKey(key) && session.status() == CloudSession.Status.LIVE
                && !session.doc(key)
                    .creating());

        // A team mate's new plan is the player's at once: in the planner, with no "shared with you".
        designs.put(ALEX + ":d3", design(ALEX, "d3", "Boilers"));
        server.send(
            "{\"t\":\"shared\",\"doc\":\"" + ALEX
                + ":d3\",\"owner\":{\"id\":\""
                + ALEX
                + "\",\"username\":\"Alex\"},\"name\":\"Boilers\"}");
        tickUntil("the team mate's plan is in the planner", () -> slot(p, ALEX + ":d3") != null);
        assertTrue(host.sharedWithMe.isEmpty(), "announced: " + host.sharedWithMe);

        // The player left the team: the backend unshares the old team's plans, the player's own too.
        designs.remove(key);
        designs.remove(ALEX + ":d3");
        server.send("{\"t\":\"unshared\",\"doc\":\"" + key + "\"}");
        tickUntil("the player's own plan stays with the team", () -> slot(p, key) == null);
        assertTrue(host.notices.contains(UiText.tr("cloud.plan_left_team", "Glass")), "told: " + host.notices);
        tickUntil("the list drops the team mate's", () -> slot(p, ALEX + ":d3") == null);
    }

    @Test
    void theEndToEndRunnerPassesAgainstThisBackend() throws IOException {
        // The runner the coordinator points at a real server (TeamE2ETest), here at this stand-in.
        System.setProperty("team.site", site);
        System.setProperty("team.tokenA", STEVE_TOKEN);
        System.setProperty("team.tokenB", ALEX_TOKEN);
        try {
            new TeamE2ETest().againstARealTeamBackend();
        } finally {
            for (final String k : new String[] { "team.site", "team.tokenA", "team.tokenB" }) System.clearProperty(k);
        }
        assertTrue(websiteHeard.isEmpty(), "the website heard: " + websiteHeard);
    }

    @Test
    void aNewSessionFromTheBackendIsTakenAndLeavingSendsNothingMore() {
        attach(STEVE_TOKEN, SERVER);
        begin();
        final CloudSession first = session;
        // The server's refreshed session for the same player: the same plans, the new session on the next call.
        attach(STEVE_TOKEN_2, SERVER);
        assertSame(first, CloudHooks.session(dir, host));
        session.refreshList();
        tickUntil("the next call takes the new session", () -> heard("GET " + P + "/api/library", STEVE_TOKEN_2));

        // Another server's store: plans of its own, kept apart.
        attach(STEVE_TOKEN_2, "another-server");
        begin();
        assertNotSame(first, session);
        assertEquals(new File(dir, "gtnhplanner/team/another-server/" + STEVE), session.folder());
        tickUntil("live on the other store", () -> session.status() == CloudSession.Status.LIVE);

        // Left the server: the session it gave goes nowhere, and nothing goes to the website in its place.
        TeamBackend.detach();
        ticks(40);
        final int before = server.requests.size();
        session.refreshList();
        ticks(100);
        assertEquals(
            before,
            server.requests.size(),
            "nothing more: " + server.requests.subList(before, server.requests.size()));
        assertTrue(websiteHeard.isEmpty(), "the website heard: " + websiteHeard);
        session.stop();
        session = null;
        // Every copy is under the team's folders: none under the website's.
        final List<String> roots = new ArrayList<>();
        final File[] under = new File(dir, "gtnhplanner").listFiles();
        if (under != null) for (final File f : under) roots.add(f.getName());
        assertEquals(List.of("team"), roots);
        assertTrue(
            new File(dir, "gtnhplanner/team/" + SERVER + "/" + STEVE).isDirectory()
                && new File(dir, "gtnhplanner/team/another-server/" + STEVE).isDirectory());
    }
}

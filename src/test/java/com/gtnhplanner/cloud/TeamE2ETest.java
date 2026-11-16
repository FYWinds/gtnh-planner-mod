package com.gtnhplanner.cloud;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.gtnhplanner.cloud.map.CloudPlans;
import com.gtnhplanner.cloud.map.TestGame;
import com.gtnhplanner.cloud.net.CloudApi;
import com.gtnhplanner.cloud.net.LiveClient;
import com.gtnhplanner.cloud.sync.LiveOp;
import com.gtnhplanner.cloud.sync.LiveOps;
import com.gtnhplanner.data.flowchart.Graph;
import com.gtnhplanner.data.flowchart.TaskEdits;
import com.gtnhplanner.data.flowchart.Tasks;

/**
 * The planner's own cloud client (REST, the live socket, the op engine and the plan mapping) against a real game
 * server's team backend, without the game: two players of one team (and, given, one outside it) make, list, open and
 * edit a task board together. Skipped unless the backend and two sessions are given:
 *
 * <pre>
 * ./gradlew test --tests com.gtnhplanner.cloud.TeamE2ETest -Pteam.site=http://HOST:25565/etotweaks/planner
 *     -Pteam.tokenA=TOKEN_A -Pteam.tokenB=TOKEN_B [-Pteam.tokenC=TOKEN_C] [-Pteam.live=ws://HOST:25565/etotweaks/planner/live]
 * </pre>
 *
 * ({@code -D} works as {@code -P}.) One PASS or FAIL line a step on the console and in {@code build/team-e2e.log}; any
 * FAIL fails the test, so the build exits non-zero. The plan it makes is deleted at the end.
 */
class TeamE2ETest {

    private static final long WAIT_MS = 15_000;

    private final List<String> lines = new ArrayList<>();
    private boolean failed;
    private final TestGame game = new TestGame();

    @Nullable
    private static String prop(final String name) {
        final String v = System.getProperty(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** One step: PASS with what it found, or FAIL with why; a failed step stops the run. */
    private interface Step {

        String run() throws Exception;
    }

    private boolean step(final String name, final Step s) {
        if (failed) return false;
        try {
            say("PASS  " + name + ": " + s.run());
            return true;
        } catch (final Exception | AssertionError e) {
            failed = true;
            say("FAIL  " + name + ": " + e);
            return false;
        }
    }

    private void say(final String line) {
        lines.add(line);
        System.out.println(line);
    }

    private static void check(final boolean ok, final String why) {
        if (!ok) throw new AssertionError(why);
    }

    /** A player's live connection, polled while waiting. */
    private static final class Player implements LiveClient.Listener {

        final String name;
        final LiveClient client;
        final List<LiveClient.Snapshot> snapshots = new CopyOnWriteArrayList<>();
        final List<LiveClient.OpsBatch> ops = new CopyOnWriteArrayList<>();
        final List<String> kicked = new CopyOnWriteArrayList<>(), errors = new CopyOnWriteArrayList<>();

        Player(final String name, final String live, final String token) {
            this.name = name;
            client = new LiveClient(URI.create(live), () -> token, new LiveClient.Hello("team-e2e", null, null));
            client.addListener(this);
            client.start();
        }

        @Override
        public void onSnapshot(final LiveClient.Snapshot snapshot) {
            snapshots.add(snapshot);
        }

        @Override
        public void onOps(final LiveClient.OpsBatch batch) {
            ops.add(batch);
        }

        @Override
        public void onKicked(final String doc, final String reason) {
            kicked.add(reason);
        }

        @Override
        public void onError(final String doc, final String code, final String message) {
            errors.add(code + " " + message);
        }
    }

    private final List<Player> players = new ArrayList<>();

    private void waitFor(final String what, final BooleanSupplier done) throws InterruptedException {
        final long until = System.currentTimeMillis() + WAIT_MS;
        while (!done.getAsBoolean()) {
            for (final Player p : players) p.client.poll();
            if (System.currentTimeMillis() > until) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }

    private static String now() {
        return Instant.now()
            .truncatedTo(ChronoUnit.MILLIS)
            .toString();
    }

    @Nullable
    private static Tasks.Task task(final JsonObject plan, final String id, final TestGame game) {
        return TaskEdits.task(
            CloudPlans.read(plan, game)
                .graph(),
            id);
    }

    @Test
    void againstARealTeamBackend() throws IOException {
        final String site = prop("team.site"), tokenA = prop("team.tokenA"), tokenB = prop("team.tokenB"),
            tokenC = prop("team.tokenC");
        assumeTrue(site != null && tokenA != null && tokenB != null, "no team.site, team.tokenA and team.tokenB given");
        final String live = prop("team.live") != null ? prop("team.live")
            : (site.startsWith("https:") ? "wss:" + site.substring(6) : "ws:" + site.substring(5)) + "/live";
        say("team backend " + site + ", live " + live + (tokenC != null ? ", with an outsider" : ""));
        // As TeamBackend.attach does for the game's own session: each session goes to this backend only.
        for (final String t : new String[] { tokenA, tokenB, tokenC }) if (t != null) CloudApi.useTeam(t, site, live);

        final CloudApi.Account[] who = new CloudApi.Account[3];
        final String designId = UUID.randomUUID()
            .toString(),
            taskId = UUID.randomUUID()
                .toString(),
            commentId = UUID.randomUUID()
                .toString();
        final String[] key = new String[1];
        final JsonObject[] seen = new JsonObject[2];
        try {
            step("health (GET " + site + "/live/health)", () -> {
                check(CloudApi.hasLive(tokenA), "the live service did not answer 200");
                return "200";
            });
            step("me as A (GET /api/community/auth/me)", () -> {
                who[0] = CloudApi.me(tokenA);
                check(who[0] != null, "401: the session was not taken");
                return who[0].id() + " " + who[0].username();
            });
            step("me as B", () -> {
                who[1] = CloudApi.me(tokenB);
                check(who[1] != null, "401: the session was not taken");
                check(
                    !who[1].id()
                        .equals(who[0].id()),
                    "A and B are the same player");
                return who[1].id() + " " + who[1].username();
            });
            step("A's library (GET /api/library)", () -> {
                final CloudApi.Library lib = CloudApi.library(tokenA, who[0].id());
                return lib.own()
                    .size() + " own, "
                    + lib.shared()
                        .size()
                    + " from the team";
            });
            step("A creates a task board with a task (PUT /api/library/designs/<id>)", () -> {
                final Graph g = new Graph("Team e2e " + now());
                g.setPrimaryView(Tasks.TASKS);
                TaskEdits.add(g, taskId, "Build the EBF", Tasks.Status.TODO, List.of(), who[0].id(), now());
                final JsonObject plan = CloudPlans.write(g, game, null, null)
                    .plan();
                final String at = now();
                final CloudApi.Put put = CloudApi.put(
                    tokenA,
                    designId,
                    new CloudApi.Upsert(g.getName(), null, null, false, false, null, at, at, at, plan));
                check(!put.live(), "409 live: the plan is open in a room already");
                key[0] = who[0].id() + ":" + designId;
                return key[0];
            });
            step("A's library lists it as A's", () -> {
                boolean found = false;
                for (final CloudApi.Design d : CloudApi.library(tokenA, who[0].id())
                    .own())
                    found |= d.id()
                        .equals(designId) && !d.deleted();
                check(found, "not among A's designs");
                return "in designs";
            });
            step("B sees it in B's library", () -> {
                for (final CloudApi.Design d : CloudApi.library(tokenB, who[1].id())
                    .shared())
                    if (d.key()
                        .equals(key[0])) return "in shared, owner " + d.ownerName();
                throw new AssertionError("not among B's shared designs");
            });
            step("B fetches it (GET /api/library/designs/<id>?owner=<A>)", () -> {
                final CloudApi.Fetched f = CloudApi.plan(tokenB, who[0].id(), designId);
                check(task(f.plan(), taskId, game) != null, "the plan has no task " + taskId);
                check("editor".equals(f.role()), "role " + f.role() + ", not editor");
                return "seq " + f.seq() + ", role " + f.role();
            });
            step("its members (GET /api/library/designs/<id>/members)", () -> {
                final CloudApi.Sharing s = CloudApi.sharing(tokenA, designId, null);
                boolean b = false;
                for (final LiveClient.Member m : s.members()) b |= m.id()
                    .equals(who[1].id());
                check(b, "B is not among the members " + s.members());
                return "owner " + s.ownerName()
                    + ", "
                    + s.members()
                        .size()
                    + " members";
            });
            step("search the team (GET /api/accounts/search?q=)", () -> {
                final String q = who[1].username()
                    .substring(
                        0,
                        Math.min(
                            3,
                            who[1].username()
                                .length()));
                boolean b = false;
                for (final CloudApi.Account a : CloudApi.search(tokenA, q)) b |= a.id()
                    .equals(who[1].id());
                check(b, "B not found by '" + q + "'");
                return "B found by '" + q + "'";
            });
            final Player a = new Player("A", live, tokenA), b = new Player("B", live, tokenB);
            players.add(a);
            players.add(b);
            step("A connects (ws " + live + ")", () -> {
                waitFor("A's welcome", a.client::ready);
                check(
                    a.client.you()
                        .id()
                        .equals(who[0].id()),
                    "welcomed as " + a.client.you()
                        .id());
                return "welcomed as " + a.client.you()
                    .username();
            });
            step("B connects", () -> {
                waitFor("B's welcome", b.client::ready);
                check(
                    b.client.you()
                        .id()
                        .equals(who[1].id()),
                    "welcomed as " + b.client.you()
                        .id());
                return "welcomed as " + b.client.you()
                    .username();
            });
            step("A and B join the room", () -> {
                a.client.join(key[0]);
                b.client.join(key[0]);
                waitFor("both snapshots", () -> !a.snapshots.isEmpty() && !b.snapshots.isEmpty());
                final LiveClient.Snapshot sa = a.snapshots.get(0), sb = b.snapshots.get(0);
                check(task(sb.plan(), taskId, game) != null, "B's snapshot has no task");
                seen[0] = sa.plan();
                seen[1] = sb.plan();
                return "A " + sa.role() + " at seq " + sa.seq() + ", B " + sb.role() + " at seq " + sb.seq();
            });
            step("B comments on the task and moves it to Doing", () -> {
                final LiveClient.Snapshot sb = b.snapshots.get(0);
                final CloudPlans.Read read = CloudPlans.read(sb.plan(), game);
                final Graph g = read.graph();
                TaskEdits.move(g, taskId, Tasks.Status.DOING, 0, now());
                g.taskComments
                    .add(new Tasks.Comment(commentId, taskId, who[1].id(), who[1].username(), now(), "Hello from B"));
                final List<LiveOp> ops = CloudPlans.diff(
                    sb.plan(),
                    CloudPlans.write(g, game, sb.plan(), read.basis())
                        .plan());
                check(!ops.isEmpty(), "nothing to send");
                check(b.client.sendOps(key[0], sb.seq(), "e2e-b-1", LiveOp.toJson(ops)), "not connected");
                waitFor(
                    "B's batch to be taken",
                    () -> b.ops.stream()
                        .anyMatch(x -> "e2e-b-1".equals(x.cid())));
                final LiveClient.OpsBatch echo = b.ops.stream()
                    .filter(x -> "e2e-b-1".equals(x.cid()))
                    .findFirst()
                    .get();
                check(
                    echo.by()
                        .equals(who[1].id()),
                    "stamped as " + echo.by());
                return ops.size() + " ops, taken at seq " + echo.seq();
            });
            step("A receives B's edit", () -> {
                waitFor(
                    "B's batch at A",
                    () -> a.ops.stream()
                        .anyMatch(
                            x -> x.by()
                                .equals(who[1].id())));
                JsonObject plan = seen[0];
                for (final LiveClient.OpsBatch x : a.ops) plan = LiveOps.apply(plan, LiveOp.fromJson(x.ops()));
                seen[0] = plan;
                final Graph g = CloudPlans.read(plan, game)
                    .graph();
                final Tasks.Task t = TaskEdits.task(g, taskId);
                check(
                    t != null && t.status() == Tasks.Status.DOING,
                    "the task is " + (t == null ? "gone" : t.status()));
                final List<Tasks.Comment> comments = TaskEdits.comments(g, taskId);
                check(
                    comments.size() == 1 && comments.get(0)
                        .text()
                        .equals("Hello from B"),
                    "comments " + comments);
                final Tasks.Comment c = comments.get(0);
                check(
                    c.by()
                        .equals(who[1].id())
                        && c.byName()
                            .equals(who[1].username()),
                    "the comment is by " + c.by() + " " + c.byName());
                return "status doing, comment by " + c.byName() + " at " + c.at();
            });
            step("A retitles the task and B receives it", () -> {
                final JsonObject before = seen[0];
                final CloudPlans.Read read = CloudPlans.read(before, game);
                final Graph g = read.graph();
                TaskEdits.put(g, TaskEdits.withTitle(TaskEdits.task(g, taskId), "Build two EBFs"));
                final List<LiveOp> ops = CloudPlans.diff(
                    before,
                    CloudPlans.write(g, game, before, read.basis())
                        .plan());
                final long base = a.ops.stream()
                    .mapToLong(LiveClient.OpsBatch::seq)
                    .max()
                    .orElse(
                        a.snapshots.get(0)
                            .seq());
                check(a.client.sendOps(key[0], base, "e2e-a-1", LiveOp.toJson(ops)), "not connected");
                waitFor(
                    "A's batch at B",
                    () -> b.ops.stream()
                        .anyMatch(x -> "e2e-a-1".equals(x.cid())));
                final LiveClient.OpsBatch got = b.ops.stream()
                    .filter(x -> "e2e-a-1".equals(x.cid()))
                    .findFirst()
                    .get();
                check(
                    got.by()
                        .equals(who[0].id()),
                    "stamped as " + got.by());
                return "seq " + got.seq();
            });
            if (tokenC != null) {
                step("C (outside the team) is told no", () -> {
                    final CloudApi.Account c = CloudApi.me(tokenC);
                    check(c != null, "401: C's session was not taken");
                    for (final CloudApi.Design d : CloudApi.library(tokenC, c.id())
                        .shared())
                        check(
                            !d.key()
                                .equals(key[0]),
                            "C's library lists it");
                    try {
                        CloudApi.plan(tokenC, who[0].id(), designId);
                        throw new AssertionError("C could fetch it");
                    } catch (final CloudApi.Refused r) {
                        check(r.status == 403 || r.status == 404, "C's fetch got " + r.status);
                    }
                    final Player cp = new Player("C", live, tokenC);
                    players.add(cp);
                    waitFor("C's welcome", cp.client::ready);
                    cp.client.join(key[0]);
                    waitFor(
                        "C to be refused the room",
                        () -> !cp.kicked.isEmpty() || !cp.errors.isEmpty() || !cp.snapshots.isEmpty());
                    check(cp.snapshots.isEmpty(), "C got the plan's snapshot");
                    return "not listed, fetch refused, room refused (" + cp.kicked + cp.errors + ")";
                });
            }
        } finally {
            for (final Player p : players) p.client.stop();
            if (key[0] != null) {
                final boolean was = failed;
                failed = false;
                step("A deletes the plan (DELETE /api/library/designs/<id>)", () -> {
                    CloudApi.delete(tokenA, designId);
                    for (final CloudApi.Design d : CloudApi.library(tokenB, who[1].id())
                        .shared())
                        check(
                            !d.key()
                                .equals(key[0]),
                            "B's library still lists it");
                    return "gone from B's library";
                });
                failed |= was;
            }
            say("RESULT: " + (failed ? "FAIL" : "PASS"));
            final Path log = Path.of("build", "team-e2e.log");
            Files.createDirectories(log.getParent());
            Files.write(log, lines, StandardCharsets.UTF_8);
        }
        if (failed) fail("the team backend failed a step; see build/team-e2e.log:\n" + String.join("\n", lines));
    }
}

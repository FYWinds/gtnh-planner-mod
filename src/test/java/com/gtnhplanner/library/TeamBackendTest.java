package com.gtnhplanner.library;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.gtnhplanner.api.TeamBackend;
import com.gtnhplanner.cloud.map.game.GameCloud;
import com.gtnhplanner.cloud.net.CloudApi;
import com.gtnhplanner.cloud.session.game.CloudHooks;
import com.sun.net.httpserver.HttpServer;

/**
 * A team backend attached and detached ({@link TeamBackend}): who cloud plans are for, where they go, where this device
 * keeps them and the world key switch to the team's and back, the website's sign-in stays as it was on disk, and the
 * team's session never goes with a request to the website (the community library, feedback).
 */
class TeamBackendTest {

    private static final String WEB_TOKEN = "web-id.9999999999999.websig";
    private static final String STEVE = "0f8fad5b-d9cb-469f-a165-70867728950e",
        SERVER = "c3d4e5f6-0000-4000-8000-000000000001", SITE = "http://play.example.net:25565/etotweaks/planner",
        LIVE = "ws://play.example.net:25565/etotweaks/planner/live";
    private static final String TEAM_TOKEN = STEVE + ".1800000000000.aaaa",
        TEAM_TOKEN_2 = STEVE + ".1800000000001.bbbb";

    @TempDir
    File dir;

    private File accountFile;
    private byte[] accountBytes;
    private final AtomicInteger changes = new AtomicInteger();
    private HttpServer website;
    private final List<String> cookies = new CopyOnWriteArrayList<>();
    private final String siteWas = CommunityApi.site();

    @BeforeEach
    void start() throws IOException {
        accountFile = new File(dir, "config/gtnhplanner-account.properties");
        accountFile.getParentFile()
            .mkdirs();
        Files.write(
            accountFile.toPath(),
            ("session=" + WEB_TOKEN + "\nusername=webby\ndevice=gtnhplanner-test\n").getBytes(StandardCharsets.UTF_8));
        accountBytes = Files.readAllBytes(accountFile.toPath());
        Account.useFile(accountFile);
        Account.onChange(changes::incrementAndGet);
        website = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        website.createContext("/", ex -> {
            ex.getRequestBody()
                .readAllBytes();
            final String cookie = ex.getRequestHeaders()
                .getFirst("Cookie");
            cookies.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + (cookie == null ? "-" : cookie));
            final byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(out);
            }
        });
        website.start();
        CommunityApi.useSite(
            "http://127.0.0.1:" + website.getAddress()
                .getPort());
    }

    @AfterEach
    void stop() {
        TeamBackend.detach();
        Account.onChange(null);
        website.stop(0);
        CommunityApi.useSite(siteWas);
    }

    private void attach(final String token, final String server) {
        TeamBackend.attach(SITE, LIVE, token, STEVE, "Steve", server, "world:team-world");
    }

    @Test
    void attachingAndDetachingSwitchWhoAndWhereAndComeBack() throws IOException {
        // The website's account, as signed in on this computer.
        assertEquals("web-id", Account.accountId());
        assertEquals("webby", Account.username());
        assertNull(Account.team());
        assertEquals(new File(dir, "gtnhplanner/cloud/web-id"), CloudHooks.folder(dir));
        final String websiteLive = CloudApi.live();
        assertEquals(CommunityApi.site(), CloudApi.site(WEB_TOKEN));

        attach(TEAM_TOKEN, SERVER);
        assertEquals(1, changes.get(), "heard as a sign-in is: the plans load from the team");
        assertEquals(STEVE, Account.accountId());
        assertEquals(STEVE, Account.userId());
        assertEquals("Steve", Account.username());
        assertEquals(WEB_TOKEN, Account.token(), "the website's session is left as it is");
        assertTrue(Account.signedIn());
        assertEquals(new File(dir, "gtnhplanner/team/" + SERVER + "/" + STEVE), CloudHooks.folder(dir));
        assertEquals("world:team-world", GameCloud.worldKey(), "the server's world key, not the typed address's");
        assertEquals(SITE, CloudApi.site(TEAM_TOKEN));
        assertEquals(LIVE, CloudApi.live(TEAM_TOKEN));
        assertEquals(CommunityApi.site(), CloudApi.site(WEB_TOKEN), "the website's session still goes to the website");
        assertEquals(websiteLive, CloudApi.live());
        assertFalse(
            Account.team()
                .toString()
                .contains(TEAM_TOKEN),
            "a log line never carries the session");

        // The same server and player again: only the session changes, nothing loads again.
        attach(TEAM_TOKEN_2, SERVER);
        assertEquals(1, changes.get());
        assertEquals(
            TEAM_TOKEN_2,
            Account.team()
                .token());
        assertEquals(SITE, CloudApi.site(TEAM_TOKEN), "a call still under way with the old session goes to the server");
        assertEquals(SITE, CloudApi.site(TEAM_TOKEN_2));

        // Another server: another place on this device.
        attach(TEAM_TOKEN_2, "another-server");
        assertEquals(2, changes.get());
        assertEquals(new File(dir, "gtnhplanner/team/another-server/" + STEVE), CloudHooks.folder(dir));

        TeamBackend.detach();
        assertEquals(3, changes.get());
        assertNull(Account.team());
        assertEquals("web-id", Account.accountId());
        assertEquals("webby", Account.username());
        assertEquals(WEB_TOKEN, Account.token());
        assertEquals(new File(dir, "gtnhplanner/cloud/web-id"), CloudHooks.folder(dir));
        assertEquals(websiteLive, CloudApi.live());
        assertEquals(SITE, CloudApi.site(TEAM_TOKEN), "a team's session never goes to the website, even after");
        TeamBackend.detach();
        assertEquals(3, changes.get(), "detaching with none attached changes nothing");

        assertArrayEquals(
            accountBytes,
            Files.readAllBytes(accountFile.toPath()),
            "nothing of the team is written to the account file");
    }

    @Test
    void signedOutTheTeamIsStillWhoThePlansAreFor() throws IOException {
        Files.write(accountFile.toPath(), "username=webby\n".getBytes(StandardCharsets.UTF_8));
        Account.useFile(accountFile);
        assertNull(Account.accountId());
        assertNull(CloudHooks.folder(dir));
        attach(TEAM_TOKEN, SERVER);
        assertEquals(STEVE, Account.accountId());
        assertFalse(Account.signedIn(), "not signed in to the website");
        assertEquals(new File(dir, "gtnhplanner/team/" + SERVER + "/" + STEVE), CloudHooks.folder(dir));
        TeamBackend.detach();
        assertNull(Account.accountId());
    }

    @Test
    void anAttachThatCannotWorkIsRefused() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TeamBackend.attach(SITE, LIVE, " ", STEVE, "Steve", SERVER, "world:k"));
        assertThrows(
            IllegalArgumentException.class,
            () -> TeamBackend.attach("ftp://x", LIVE, TEAM_TOKEN, STEVE, "Steve", SERVER, "world:k"));
        assertThrows(
            IllegalArgumentException.class,
            () -> TeamBackend.attach(SITE, SITE, TEAM_TOKEN, STEVE, "Steve", SERVER, "world:k"));
        assertNull(Account.team());
        assertEquals(0, changes.get());
    }

    @Test
    void theCommunityLibraryAndFeedbackNeverCarryTheTeamsSession() throws Exception {
        attach(TEAM_TOKEN, SERVER);
        // The community list, as the library fetches it (LibraryFeed picks the session).
        final LibraryFeed feed = new LibraryFeed();
        feed.restart();
        final long until = System.currentTimeMillis() + 10_000;
        while (cookies.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(10);
        // Feedback, which goes with no session at all.
        final JsonObject report = new JsonObject();
        report.addProperty("text", "hello");
        try {
            CommunityApi.report(report);
        } catch (final IOException expected) {
            // The stand-in answers {}: no link, so no report; what was sent is what counts.
        }
        assertEquals(2, cookies.size(), "the list and the report: " + cookies);
        assertTrue(
            cookies.get(0)
                .startsWith("GET /api/community/plans")
                && cookies.get(0)
                    .endsWith("gtnh_session=" + WEB_TOKEN),
            "the list goes with the website's session: " + cookies.get(0));
        assertEquals("POST /api/feedback -", cookies.get(1));
        for (final String c : cookies) assertFalse(c.contains(TEAM_TOKEN), c);
    }
}

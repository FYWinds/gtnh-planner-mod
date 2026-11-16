package com.gtnhplanner.cloud;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnhplanner.cloud.sync.LiveOp;
import com.gtnhplanner.cloud.sync.LiveOps;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandler;

/**
 * A live service stand-in on netty's server side: plain or TLS, it checks the session cookie on the upgrade and answers
 * the protocol the way the real one does for what the tests drive (hello, join, ops, presence, ping), recording every
 * frame it is sent.
 */
final class LiveTestServer implements AutoCloseable {

    final List<String> received = new CopyOnWriteArrayList<>();
    final List<String> cookies = new CopyOnWriteArrayList<>();
    final List<Channel> clients = new CopyOnWriteArrayList<>();
    final AtomicInteger connections = new AtomicInteger(), pings = new AtomicInteger();
    /** Upgrades are refused with 401 while this is set. */
    volatile boolean refuseAuth;
    /** Pings are answered with a pong frame (the real service does); off to look like a dead peer. */
    volatile boolean answerPings = true;
    /** The doc's plan in the snapshot a join gets. */
    volatile JsonObject plan = new JsonObject();

    /**
     * Rooms, as the real service keeps them: each doc's plan and seq ({@link #plans}, {@link #seqs}), every batch
     * applied once by its cid (one sent again is acknowledged to its sender alone, with no ops) and sent to everyone in
     * the room. Off, a join gets {@link #plan} and a batch is echoed to its sender.
     */
    volatile boolean rooms;
    final Map<String, JsonObject> plans = new ConcurrentHashMap<>();
    final Map<String, Long> seqs = new ConcurrentHashMap<>();
    /** Batches applied, by doc and cid: the seq each got. */
    final Map<String, Long> applied = new ConcurrentHashMap<>();
    /** Every batch is refused with this code while set (rooms only). */
    volatile String refuse;
    /** How many batches `refuse` still refuses before it lets them through (rooms only). */
    final AtomicInteger refuseTimes = new AtomicInteger(Integer.MAX_VALUE);
    /** The next this many joins are answered with a `rate` error instead of a snapshot (rooms only). */
    final AtomicInteger rateJoins = new AtomicInteger();
    /** Changes the ops of every accepted batch before they are applied and echoed, as the schema's output does. */
    volatile java.util.function.UnaryOperator<JsonArray> correct;
    /** Upgrades are answered with this HTTP status while it is set (not 0). */
    volatile int upgradeStatus;
    /** Upgrades refused with `upgradeStatus`. */
    final AtomicInteger upgrades = new AtomicInteger();
    volatile String retryAfter;
    private final Map<Channel, java.util.Set<String>> in = new ConcurrentHashMap<>();
    private final AtomicInteger others = new AtomicInteger();

    /**
     * Mounted under a path, as a game server's team backend is ({@code /etotweaks/planner}): the socket is
     * {@code <prefix>/live}, its health {@code <prefix>/live/health}, every other request under the prefix goes to
     * {@link #rest}, anything else is 404, and every request needs a session ({@link #accounts}). Empty: any path
     * upgrades and only the upgrade's cookie is checked, as before.
     */
    volatile String prefix = "";
    /**
     * Who each session is ({@code gtnh_session} to {id, username}). With any set (or a prefix), only these get in; each
     * is welcomed as itself, and its batches are stamped with its id. None: everyone is u1, ada.
     */
    final Map<String, String[]> accounts = new ConcurrentHashMap<>();
    /** Every HTTP request under a prefix, in order: "METHOD uri session". */
    final List<String> requests = new CopyOnWriteArrayList<>();
    /** The REST routes under the prefix: the method, the uri past the prefix, who asks, the body; {status, body}. */
    volatile Rest rest;
    /** Who each socket is (a prefix only). */
    private final Map<Channel, String[]> who = new ConcurrentHashMap<>();

    interface Rest {

        String[] answer(String method, String uri, String[] who, String body);
    }

    /** The id a socket's batches are stamped with. */
    private String by(final ChannelHandlerContext ctx) {
        return who.getOrDefault(ctx.channel(), new String[] { "u1", "ada" })[0];
    }

    private final NioEventLoopGroup group = new NioEventLoopGroup(2);
    private final Channel server;
    private final AtomicInteger seq = new AtomicInteger();

    /** Someone else's batch on a doc (rooms only): applied, and sent to everyone in its room. */
    synchronized void others(final String doc, final JsonArray ops) {
        apply(doc, "other-" + others.incrementAndGet(), "u2", ops);
    }

    /** Applies a batch to a doc and sends it to its room; its seq. */
    private synchronized long apply(final String doc, final String cid, final String by, final JsonArray ops) {
        final JsonObject was = plans.getOrDefault(doc, new JsonObject());
        plans.put(doc, LiveOps.apply(was, LiveOp.fromJson(ops)));
        final long s = seqs.getOrDefault(doc, 0L) + 1;
        seqs.put(doc, s);
        applied.put(doc + " " + cid, s);
        final JsonObject o = new JsonObject();
        o.addProperty("t", "ops");
        o.addProperty("doc", doc);
        o.addProperty("seq", s);
        o.addProperty("by", by);
        o.addProperty("cid", cid);
        o.add("ops", ops);
        for (final Map.Entry<Channel, java.util.Set<String>> e : in.entrySet()) if (e.getValue()
            .contains(doc))
            e.getKey()
                .writeAndFlush(new TextWebSocketFrame(o.toString()));
        return s;
    }

    private synchronized void room(final ChannelHandlerContext ctx, final JsonObject f, final String t) {
        final String doc = f.has("doc") ? f.get("doc")
            .getAsString() : "";
        switch (t) {
            case "join" -> {
                if (rateJoins.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    final JsonObject e = new JsonObject();
                    e.addProperty("t", "error");
                    e.addProperty("doc", doc);
                    e.addProperty("code", "rate");
                    e.addProperty("msg", "slow down");
                    ctx.writeAndFlush(new TextWebSocketFrame(e.toString()));
                    return;
                }
                in.computeIfAbsent(ctx.channel(), c -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(doc);
                final JsonObject s = new JsonObject();
                s.addProperty("t", "snapshot");
                s.addProperty("doc", doc);
                s.addProperty("seq", seqs.getOrDefault(doc, 0L));
                s.add("plan", plans.getOrDefault(doc, new JsonObject()));
                // The doc key's owner, as the accounts know them (none: u1, ada, the owner).
                final String ownerId = accounts.isEmpty() || !doc.contains(":") ? "u1"
                    : doc.substring(0, doc.indexOf(':'));
                String ownerName = "ada";
                for (final String[] a : accounts.values()) if (a[0].equals(ownerId)) ownerName = a[1];
                s.addProperty("role", ownerId.equals(by(ctx)) ? "owner" : "editor");
                final JsonObject owner = new JsonObject();
                owner.addProperty("id", ownerId);
                owner.addProperty("username", ownerName);
                s.add("owner", owner);
                s.add("members", new JsonArray());
                s.add("present", new JsonArray());
                ctx.writeAndFlush(new TextWebSocketFrame(s.toString()));
            }
            case "leave" -> {
                final java.util.Set<String> docs = in.get(ctx.channel());
                if (docs != null) docs.remove(doc);
            }
            case "ops" -> {
                final String cid = f.get("cid")
                    .getAsString();
                if (refuse != null && refuseTimes.getAndDecrement() > 0) {
                    final JsonObject e = new JsonObject();
                    e.addProperty("t", "error");
                    e.addProperty("doc", doc);
                    e.addProperty("cid", cid);
                    e.addProperty("code", refuse);
                    e.addProperty("msg", "refused");
                    ctx.writeAndFlush(new TextWebSocketFrame(e.toString()));
                    return;
                }
                final Long had = applied.get(doc + " " + cid);
                if (had != null) {
                    final JsonObject o = new JsonObject();
                    o.addProperty("t", "ops");
                    o.addProperty("doc", doc);
                    o.addProperty("seq", had);
                    o.addProperty("by", by(ctx));
                    o.addProperty("cid", cid);
                    o.add("ops", new JsonArray());
                    ctx.writeAndFlush(new TextWebSocketFrame(o.toString()));
                    return;
                }
                final java.util.function.UnaryOperator<JsonArray> fix = correct;
                apply(doc, cid, by(ctx), fix == null ? f.getAsJsonArray("ops") : fix.apply(f.getAsJsonArray("ops")));
            }
            default -> {}
        }
    }

    LiveTestServer(@Nullable final SSLContext ssl) throws InterruptedException {
        server = new ServerBootstrap().group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(final SocketChannel ch) {
                    if (ssl != null) {
                        final SSLEngine engine = ssl.createSSLEngine();
                        engine.setUseClientMode(false);
                        ch.pipeline()
                            .addLast(new SslHandler(engine));
                    }
                    ch.pipeline()
                        .addLast(new HttpServerCodec(), new HttpObjectAggregator(8 << 20), new Handler());
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0))
            .sync()
            .channel();
    }

    int port() {
        return ((InetSocketAddress) server.localAddress()).getPort();
    }

    /** Sends a frame to every connected client. */
    void send(final String text) {
        for (final Channel c : clients) c.writeAndFlush(new TextWebSocketFrame(text));
    }

    /** Sends one message in two fragments. */
    void sendFragmented(final String first, final String second) {
        for (final Channel c : clients) {
            c.write(new TextWebSocketFrame(false, 0, first));
            c.writeAndFlush(new ContinuationWebSocketFrame(true, 0, second));
        }
    }

    /** Closes every client's connection with a WebSocket close frame carrying {@code code}. */
    void closeAll(final int code) {
        for (final Channel c : clients)
            c.writeAndFlush(new io.netty.handler.codec.http.websocketx.CloseWebSocketFrame(code, ""))
                .addListener(ChannelFutureListener.CLOSE);
        clients.clear();
    }

    /** Closes every client's connection without a word, as a network that went away does. */
    void dropAll() {
        for (final Channel c : clients) c.close();
        clients.clear();
    }

    List<JsonObject> framesOf(final String type) {
        final List<JsonObject> out = new java.util.ArrayList<>();
        for (final String s : received) {
            final JsonObject o = new JsonParser().parse(s)
                .getAsJsonObject();
            if (type.equals(
                o.get("t")
                    .getAsString()))
                out.add(o);
        }
        return out;
    }

    @Override
    public void close() {
        server.close();
        group.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private final class Handler extends SimpleChannelInboundHandler<Object> {

        private WebSocketServerHandshaker handshaker;

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof FullHttpRequest) {
                final FullHttpRequest req = (FullHttpRequest) msg;
                if (!prefix.isEmpty() && !mounted(ctx, req)) return;
                if (prefix.isEmpty() && req.getUri()
                    .endsWith("/health")) {
                    // The service's health route, as the real one answers it: it runs.
                    ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
                        .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                final String cookie = req.headers()
                    .get(HttpHeaders.Names.COOKIE);
                cookies.add(cookie == null ? "" : cookie);
                if (upgradeStatus != 0) {
                    upgrades.incrementAndGet();
                    final DefaultFullHttpResponse r = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.valueOf(upgradeStatus));
                    if (retryAfter != null) r.headers()
                        .set("Retry-After", retryAfter);
                    ctx.writeAndFlush(r)
                        .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                if (refuseAuth || cookie == null || !cookie.startsWith("gtnh_session=")) {
                    ctx.writeAndFlush(
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED))
                        .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                handshaker = new WebSocketServerHandshakerFactory(
                    "ws://127.0.0.1:" + port() + "/live",
                    null,
                    false,
                    1 << 25).newHandshaker(req);
                handshaker.handshake(ctx.channel(), req);
                connections.incrementAndGet();
                clients.add(ctx.channel());
            } else if (msg instanceof TextWebSocketFrame) {
                final String text = ((TextWebSocketFrame) msg).text();
                received.add(text);
                respond(
                    ctx,
                    new JsonParser().parse(text)
                        .getAsJsonObject());
            } else if (msg instanceof WebSocketFrame) {
                // Pings and closes of the transport: nothing to say.
            }
        }

        /**
         * A request to a server mounted under {@link #prefix}: true for the socket's upgrade with a known session (it
         * goes on as an upgrade); anything else is answered here (health, REST, 401, 404).
         */
        private boolean mounted(final ChannelHandlerContext ctx, final FullHttpRequest req) {
            final String uri = req.getUri();
            final int q = uri.indexOf('?');
            final String path = q < 0 ? uri : uri.substring(0, q);
            final String cookie = req.headers()
                .get(HttpHeaders.Names.COOKIE);
            final String session = cookie != null && cookie.startsWith("gtnh_session=")
                ? cookie.substring("gtnh_session=".length())
                : null;
            requests.add(
                req.getMethod()
                    .name() + " "
                    + uri
                    + " "
                    + (session == null ? "-" : session));
            if (!path.startsWith(prefix + "/")) {
                http(ctx, 404, "{\"error\":\"not found\"}");
                return false;
            }
            final String[] account = session == null ? null : accounts.get(session);
            if (account == null) {
                http(ctx, 401, "{\"error\":\"not signed in\"}");
                return false;
            }
            final String route = path.substring(prefix.length());
            if (route.equals("/live")) {
                who.put(ctx.channel(), account);
                return true;
            }
            if (route.equals("/live/health")) {
                http(ctx, 200, "{\"ok\":true}");
                return false;
            }
            final Rest r = rest;
            final String[] answer = r == null ? new String[] { "404", "{\"error\":\"not found\"}" }
                : r.answer(
                    req.getMethod()
                        .name(),
                    uri.substring(prefix.length()),
                    account,
                    req.content()
                        .toString(java.nio.charset.StandardCharsets.UTF_8));
            http(ctx, Integer.parseInt(answer[0]), answer[1]);
            return false;
        }

        /** An HTTP answer, the connection closed after it. */
        private void http(final ChannelHandlerContext ctx, final int status, final String body) {
            final byte[] out = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            final DefaultFullHttpResponse r = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(status),
                io.netty.buffer.Unpooled.wrappedBuffer(out));
            r.headers()
                .set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
            r.headers()
                .set(HttpHeaders.Names.CONTENT_LENGTH, out.length);
            r.headers()
                .set(HttpHeaders.Names.CONNECTION, "close");
            ctx.writeAndFlush(r)
                .addListener(ChannelFutureListener.CLOSE);
        }

        private void respond(final ChannelHandlerContext ctx, final JsonObject f) {
            final String t = f.get("t")
                .getAsString();
            if (rooms && (t.equals("join") || t.equals("leave") || t.equals("ops"))) {
                room(ctx, f, t);
                return;
            }
            switch (t) {
                case "ping" -> {
                    pings.incrementAndGet();
                    if (answerPings) reply(ctx, "{\"t\":\"pong\"}");
                }
                case "hello" -> {
                    final String[] me = who.getOrDefault(ctx.channel(), new String[] { "u1", "ada" });
                    reply(
                        ctx,
                        "{\"t\":\"welcome\",\"v\":1,\"you\":{\"id\":\"" + me[0]
                            + "\",\"username\":\""
                            + me[1]
                            + "\",\"color\":\"#fa0\"}}");
                }
                case "join" -> {
                    final JsonObject s = new JsonObject();
                    s.addProperty("t", "snapshot");
                    s.addProperty(
                        "doc",
                        f.get("doc")
                            .getAsString());
                    s.addProperty("seq", seq.get());
                    s.add("plan", plan);
                    s.addProperty("role", "owner");
                    final JsonArray members = new JsonArray();
                    final JsonObject m = new JsonObject();
                    m.addProperty("id", "u2");
                    m.addProperty("username", "bob");
                    members.add(m);
                    s.add("members", members);
                    s.add("present", new JsonArray());
                    reply(ctx, s.toString());
                }
                case "ops" -> {
                    final JsonObject o = new JsonObject();
                    o.addProperty("t", "ops");
                    o.addProperty(
                        "doc",
                        f.get("doc")
                            .getAsString());
                    o.addProperty("seq", seq.incrementAndGet());
                    o.addProperty("by", "u1");
                    o.add("cid", f.get("cid"));
                    o.add("ops", f.get("ops"));
                    reply(ctx, o.toString());
                }
                default -> {}
            }
        }

        private void reply(final ChannelHandlerContext ctx, final String text) {
            ctx.writeAndFlush(new TextWebSocketFrame(text));
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            clients.remove(ctx.channel());
            in.remove(ctx.channel());
            who.remove(ctx.channel());
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            ctx.close();
        }
    }

}

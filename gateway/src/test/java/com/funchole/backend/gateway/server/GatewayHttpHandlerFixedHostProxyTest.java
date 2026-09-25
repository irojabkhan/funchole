package com.funchole.backend.gateway.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funchole.backend.gateway.server.FixedHostProxy.ProxyTarget;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link FixedHostProxy}/{@link FixedHostProxyForwarder} - the
 * reverse-proxy branch in {@link GatewayHttpHandler#channelRead0} for the
 * admin web app / controlplane API. Unlike {@link GatewayHttpHandlerInvocationTest},
 * this can't be a pure {@code EmbeddedChannel} unit test: the code under
 * test opens a genuine second outbound TCP connection, and {@code
 * EmbeddedChannel}'s event loop cannot register a real {@code
 * NioSocketChannel}. Instead this spins up a real (plaintext - no TLS,
 * since TLS/SNI termination is pre-existing, unmodified code) Netty server
 * hosting {@link GatewayHttpHandler} on an ephemeral port, a real
 * JDK-bundled {@link HttpServer} standing in for the internal target
 * ({@code web:3000}/{@code controlplane:7080}), and a small Netty client to
 * drive requests with an arbitrary {@code Host} header.
 */
class GatewayHttpHandlerFixedHostProxyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROXY_HOSTNAME = "admin.test";

    private EventLoopGroup gatewayGroup;
    private EventLoopGroup clientGroup;
    private Channel gatewayChannel;
    private HttpServer targetServer;
    private ExecutorService invocationExecutor;

    @AfterEach
    void tearDown() {
        if (gatewayChannel != null) {
            gatewayChannel.close();
        }
        if (gatewayGroup != null) {
            gatewayGroup.shutdownGracefully();
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
        }
        if (targetServer != null) {
            targetServer.stop(0);
        }
        if (invocationExecutor != null) {
            invocationExecutor.shutdownNow();
        }
    }

    @Test
    void forwardsToTheConfiguredTargetAndRelaysHeadersAndBody() throws Exception {
        LinkedBlockingQueue<HttpExchange> received = new LinkedBlockingQueue<>();
        startFakeTarget(exchange -> {
            received.add(exchange);
            byte[] body = "hello from target".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("X-Target-Header", "target-value");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // gatewayRegistry and flowResolver are deliberately null: the fixed
        // host must never reach either, since it has no AppDomain/Flow at
        // all. If the branch ever fell through to the normal lookup, this
        // would NPE and fail the test loudly instead of silently passing.
        int gatewayPort = startGateway(fixedHostProxy(targetServer.getAddress().getPort()));

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/admin/login",
                Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, PROXY_HOSTNAME);
        request.headers().set("X-Test-Header", "client-value");
        // A real hop-by-hop header - must never reach the target.
        request.headers().add("TE", "trailers");

        FullHttpResponse response = sendRequest(gatewayPort, request);
        try {
            assertEquals(200, response.status().code());
            assertEquals("hello from target", response.content().toString(StandardCharsets.UTF_8));
            assertEquals("target-value", response.headers().get("X-Target-Header"));
        } finally {
            response.release();
        }

        HttpExchange forwarded = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(forwarded, "target never received a request");
        assertEquals(PROXY_HOSTNAME, forwarded.getRequestHeaders().getFirst("X-Forwarded-Host"));
        assertEquals("https", forwarded.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        assertEquals("client-value", forwarded.getRequestHeaders().getFirst("X-Test-Header"));
        assertFalse(forwarded.getRequestHeaders().containsKey("TE"), "hop-by-hop header TE must not be forwarded");
    }

    @Test
    void pathOverrideRewritesUriAndForwardsToItsOwnTargetInsteadOfTheHostnameDefault() throws Exception {
        LinkedBlockingQueue<HttpExchange> received = new LinkedBlockingQueue<>();
        startFakeTarget(exchange -> {
            received.add(exchange);
            exchange.sendResponseHeaders(200, -1);
        });

        // The hostname's own default target (a dead port - never reached)
        // is deliberately distinct from the override's target (the real
        // fake server), so a path that should NOT match the override still
        // gets its own coverage failure mode: proof the override only fires
        // for matching paths, not for every request on this hostname.
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }

        FixedHostProxy proxy = new FixedHostProxy(
                Map.of(PROXY_HOSTNAME, new ProxyTarget("127.0.0.1", deadPort)),
                Map.of(PROXY_HOSTNAME, new FixedHostProxy.PathOverride(
                        "/mcp", "/api/mcp", new ProxyTarget("127.0.0.1", targetServer.getAddress().getPort()))));
        int gatewayPort = startGateway(proxy);

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/mcp?foo=bar", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, PROXY_HOSTNAME);

        FullHttpResponse response = sendRequest(gatewayPort, request);
        try {
            assertEquals(200, response.status().code());
        } finally {
            response.release();
        }

        HttpExchange forwarded = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(forwarded, "override target never received a request");
        assertEquals("/api/mcp?foo=bar", forwarded.getRequestURI().toString());
    }

    @Test
    void respondsWithBadGatewayWhenTheTargetIsUnreachable() throws Exception {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        // socket is now closed - nothing listens on deadPort.

        int gatewayPort = startGateway(fixedHostProxy(deadPort));

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/admin/login", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, PROXY_HOSTNAME);

        FullHttpResponse response = sendRequest(gatewayPort, request);
        try {
            assertEquals(502, response.status().code());
        } finally {
            response.release();
        }
    }

    private FixedHostProxy fixedHostProxy(int targetPort) {
        return new FixedHostProxy(Map.of(PROXY_HOSTNAME, new ProxyTarget("127.0.0.1", targetPort)), Map.of());
    }

    private void startFakeTarget(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        targetServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        targetServer.createContext("/", handler);
        targetServer.start();
    }

    /**
     * A plaintext (no TLS/SNI) Netty server hosting {@link GatewayHttpHandler}
     * directly - {@code SniHandler}/cert selection is pre-existing,
     * unmodified code, out of scope for this test.
     */
    private int startGateway(FixedHostProxy fixedHostProxy) throws InterruptedException {
        invocationExecutor = Executors.newSingleThreadExecutor();
        GatewayHttpHandler handler = new GatewayHttpHandler(
                OBJECT_MAPPER, null, null, null, null, invocationExecutor, null, null, fixedHostProxy);

        gatewayGroup = new NioEventLoopGroup(2);
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(gatewayGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(handler);
                    }
                });
        gatewayChannel = bootstrap.bind(0).sync().channel();
        return ((InetSocketAddress) gatewayChannel.localAddress()).getPort();
    }

    private FullHttpResponse sendRequest(int gatewayPort, FullHttpRequest request) throws Exception {
        clientGroup = new NioEventLoopGroup(1);
        CompletableFuture<FullHttpResponse> responseFuture = new CompletableFuture<>();
        Bootstrap clientBootstrap = new Bootstrap()
                .group(clientGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                    @Override
                                    protected void channelRead0(
                                            io.netty.channel.ChannelHandlerContext context, FullHttpResponse response) {
                                        responseFuture.complete(response.retainedDuplicate());
                                    }
                                });
                    }
                });
        Channel clientChannel = clientBootstrap.connect("127.0.0.1", gatewayPort).sync().channel();
        try {
            clientChannel.writeAndFlush(request).sync();
            return responseFuture.get(10, TimeUnit.SECONDS);
        } finally {
            clientChannel.close();
        }
    }
}

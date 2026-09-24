package com.funchole.backend.gateway.server;

import com.funchole.backend.gateway.server.FixedHostProxy.ProxyTarget;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reverse-proxies a single request to a {@link ProxyTarget} inside the
 * docker network and relays its response back - the mechanics behind
 * {@link FixedHostProxy}. Kept separate from {@link GatewayHttpHandler}
 * since a real outbound HTTP client, unlike the rest of that handler's
 * NATS-dispatch/static-file paths, needs its own connection lifecycle,
 * header hygiene, and failure handling.
 */
final class FixedHostProxyForwarder {
    private static final Logger logger = LoggerFactory.getLogger(FixedHostProxyForwarder.class);

    // Hop-by-hop headers (RFC 7230 6.1) are meaningful only for the single
    // connection they were set on and must never be relayed verbatim to the
    // other side of the proxy.
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "upgrade",
            "proxy-authenticate", "proxy-authorization", "te", "trailer");

    // The admin UI/API, not tenant Function traffic - a generous cap for a
    // real HTML/JS/CSS payload, well above the 64 KiB the inbound pipeline
    // aggregates request bodies to (GatewayChannelInitializer), which is
    // unrelated: that limit is for requests arriving at the Gateway, this
    // one is for responses coming back from the internal target.
    private static final int MAX_PROXIED_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_SECONDS = 30;

    private FixedHostProxyForwarder() {
    }

    /**
     * Forwards {@code request} to {@code target} and writes its response
     * back on {@code originContext}. Retains {@code request} itself for the
     * duration of the (async) forward and releases it exactly once - the
     * caller does not need to manage its lifecycle. Never throws: any
     * failure (connect refused, timeout, malformed upstream response) is
     * written back as a 502 instead of propagating, since this Gateway
     * process is shared by all tenant traffic and a bug here must stay
     * contained to the one request in flight.
     */
    static void forward(
            ChannelHandlerContext originContext,
            FullHttpRequest request,
            ProxyTarget target,
            String originalHostname
    ) {
        request.retain();
        AtomicBoolean responded = new AtomicBoolean(false);
        try {
            FullHttpRequest outboundRequest = buildOutboundRequest(request, target, originalHostname, clientAddress(originContext));
            request.release();

            new Bootstrap()
                    .group(originContext.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline()
                                    .addLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                    .addLast(new HttpClientCodec())
                                    .addLast(new HttpObjectAggregator(MAX_PROXIED_RESPONSE_BYTES))
                                    .addLast(new UpstreamResponseHandler(originContext, responded));
                        }
                    })
                    .connect(new InetSocketAddress(target.host(), target.port()))
                    .addListener((ChannelFutureListener) connectFuture -> {
                        if (!connectFuture.isSuccess()) {
                            logger.warn(
                                    "Fixed-host proxy failed to connect: target={}:{}, message={}",
                                    target.host(), target.port(), connectFuture.cause().getMessage());
                            failOnce(originContext, responded);
                            return;
                        }
                        connectFuture.channel().writeAndFlush(outboundRequest).addListener((ChannelFutureListener) writeFuture -> {
                            if (!writeFuture.isSuccess()) {
                                logger.warn(
                                        "Fixed-host proxy failed to write outbound request: target={}:{}, message={}",
                                        target.host(), target.port(), writeFuture.cause().getMessage());
                                failOnce(originContext, responded);
                            }
                        });
                    });
        } catch (RuntimeException exception) {
            logger.warn("Fixed-host proxy failed to build outbound request: message={}", exception.getMessage());
            request.release();
            failOnce(originContext, responded);
        }
    }

    private static FullHttpRequest buildOutboundRequest(
            FullHttpRequest inbound, ProxyTarget target, String originalHostname, String clientAddress
    ) {
        FullHttpRequest outbound = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                inbound.method(),
                inbound.uri(),
                inbound.content().retainedDuplicate()
        );
        copyHeaders(inbound.headers(), outbound.headers());
        outbound.headers().set(HttpHeaderNames.HOST, target.host() + ":" + target.port());
        outbound.headers().set(HttpHeaderNames.CONTENT_LENGTH, outbound.content().readableBytes());
        outbound.headers().set("X-Forwarded-Host", originalHostname);
        outbound.headers().set("X-Forwarded-Proto", "https");
        if (clientAddress != null) {
            outbound.headers().set("X-Forwarded-For", clientAddress);
        }
        return outbound;
    }

    private static void copyHeaders(HttpHeaders source, HttpHeaders destination) {
        for (Map.Entry<String, String> header : source) {
            if (!HOP_BY_HOP_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                destination.add(header.getKey(), header.getValue());
            }
        }
    }

    private static String clientAddress(ChannelHandlerContext originContext) {
        if (originContext.channel().remoteAddress() instanceof InetSocketAddress socketAddress) {
            return socketAddress.getAddress().getHostAddress();
        }
        return null;
    }

    private static void failOnce(ChannelHandlerContext originContext, AtomicBoolean responded) {
        if (responded.compareAndSet(false, true)) {
            writeBadGateway(originContext);
        }
    }

    private static void writeBadGateway(ChannelHandlerContext originContext) {
        byte[] body = "Bad Gateway".getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_GATEWAY,
                Unpooled.wrappedBuffer(body)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
        originContext.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static final class UpstreamResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final ChannelHandlerContext originContext;
        private final AtomicBoolean responded;

        UpstreamResponseHandler(ChannelHandlerContext originContext, AtomicBoolean responded) {
            this.originContext = originContext;
            this.responded = responded;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext upstreamContext, FullHttpResponse upstreamResponse) {
            if (!responded.compareAndSet(false, true)) {
                upstreamContext.close();
                return;
            }
            FullHttpResponse relayed = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    upstreamResponse.status(),
                    upstreamResponse.content().retainedDuplicate()
            );
            copyHeaders(upstreamResponse.headers(), relayed.headers());
            relayed.headers().set(HttpHeaderNames.CONTENT_LENGTH, relayed.content().readableBytes());
            originContext.writeAndFlush(relayed).addListener(ChannelFutureListener.CLOSE);
            upstreamContext.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext upstreamContext, Throwable cause) {
            logger.warn("Fixed-host proxy upstream error: message={}", cause.getMessage());
            failOnce(originContext, responded);
            upstreamContext.close();
        }
    }
}

package com.funchole.backend.gateway.acme;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain HTTP (no TLS) handler bound to port 80, solely to answer Let's
 * Encrypt's HTTP-01 domain-ownership challenge at
 * {@code /.well-known/acme-challenge/<token>}. Everything else 404s - this
 * listener has no other job, real traffic stays on the HTTPS/443 listener.
 */
@ChannelHandler.Sharable
public class AcmeChallengeHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(AcmeChallengeHandler.class);
    private static final String CHALLENGE_PATH_PREFIX = "/.well-known/acme-challenge/";

    private final AcmeChallengeLookup challengeLookup;

    public AcmeChallengeHandler(AcmeChallengeLookup challengeLookup) {
        this.challengeLookup = challengeLookup;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
        String path = request.uri();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        if (path.startsWith(CHALLENGE_PATH_PREFIX)) {
            String token = path.substring(CHALLENGE_PATH_PREFIX.length());
            Optional<String> authorization = challengeLookup.find(token);
            if (authorization.isPresent()) {
                respond(context, HttpResponseStatus.OK, authorization.get());
                return;
            }
            logger.debug("No ACME challenge found for token {}", token);
        }

        respond(context, HttpResponseStatus.NOT_FOUND, "not found");
    }

    private void respond(ChannelHandlerContext context, HttpResponseStatus status, String body) {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(content));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, content.length);
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        logger.warn("ACME challenge handler error", cause);
        context.close();
    }
}

package com.funchole.backend.gateway.server;

import com.funchole.backend.gateway.GatewayRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SniHandler;

public class GatewayChannelInitializer extends ChannelInitializer<SocketChannel> {

    // Shared by all inbound Gateway traffic: tenant Function invocations AND
    // the fixed-host-proxied public API/web traffic (FixedHostProxy), which
    // includes large MCP source-submission payloads for multi-file apps -
    // sized well above the controlplane REST multipart cap (25 MB) rather
    // than the small tenant-invocation payloads this used to be tuned for.
    private static final int MAX_INBOUND_REQUEST_BYTES = 32 * 1024 * 1024;

    private final GatewayHttpHandler gatewayHttpHandler;
    private final GatewayRegistry gatewayRegistry;

    public GatewayChannelInitializer(GatewayRegistry gatewayRegistry, GatewayHttpHandler gatewayHttpHandler) {
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayHttpHandler = gatewayHttpHandler;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline()
                .addLast(new SniHandler(gatewayRegistry.sslContextMapping()))
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(MAX_INBOUND_REQUEST_BYTES))
                .addLast(gatewayHttpHandler);
    }
}

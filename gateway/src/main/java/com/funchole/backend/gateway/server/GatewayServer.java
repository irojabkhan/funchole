package com.funchole.backend.gateway.server;

import com.funchole.backend.gateway.GatewayRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GatewayServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GatewayServer.class);

    private final int port;
    private final GatewayRegistry gatewayRegistry;
    private final GatewayHttpHandler gatewayHttpHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public GatewayServer(int port, GatewayRegistry gatewayRegistry, GatewayHttpHandler gatewayHttpHandler) {
        this.port = port;
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayHttpHandler = gatewayHttpHandler;
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new GatewayChannelInitializer(gatewayRegistry, gatewayHttpHandler));

            channel = bootstrap.bind(port).sync().channel();
            logger.info("Gateway HTTPS server listening on port {} with {} registered host(s)", port, gatewayRegistry.entries().size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to start gateway server", exception);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public void await() throws InterruptedException {
        if (channel != null) {
            channel.closeFuture().sync();
        }
    }

    /**
     * The actual bound port - needed by a caller that started this server
     * with {@code port == 0} (OS-assigned ephemeral port), e.g. a test that
     * cannot know the port in advance and must dial it after {@link #start()}.
     */
    public int boundPort() {
        if (channel == null) {
            throw new IllegalStateException("Gateway server is not started");
        }
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
    }
}

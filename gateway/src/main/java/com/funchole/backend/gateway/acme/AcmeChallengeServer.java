package com.funchole.backend.gateway.acme;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A second, separate Netty server from {@code GatewayServer} - plain HTTP,
 * no TLS/SNI, bound to a different port (80 by default). Its only job is
 * serving Let's Encrypt's HTTP-01 challenge responses; the real
 * Flow/Function traffic never touches this listener.
 */
public final class AcmeChallengeServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AcmeChallengeServer.class);

    private final int port;
    private final AcmeChallengeHandler challengeHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public AcmeChallengeServer(int port, AcmeChallengeLookup challengeLookup) {
        this.port = port;
        this.challengeHandler = new AcmeChallengeHandler(challengeLookup);
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(8192))
                                    .addLast(challengeHandler);
                        }
                    });

            channel = bootstrap.bind(port).sync().channel();
            logger.info("ACME HTTP-01 challenge server listening on port {}", port);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to start ACME challenge server", exception);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
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

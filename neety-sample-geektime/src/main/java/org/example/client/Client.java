package org.example.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.study.common.RequestMessage;
import io.netty.example.study.common.auth.AuthOperation;
import io.netty.example.study.common.order.OrderOperation;
import io.netty.example.study.util.IdUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.SneakyThrows;
import org.example.client.codec.OrderFrameDecoder;
import org.example.client.codec.OrderFrameEncoder;
import org.example.client.codec.OrderProtocolDecoder;
import org.example.client.codec.OrderProtocolEncoder;
import org.example.client.handler.ClientIdleCheckHandler;
import org.example.client.handler.KeepaliveHandler;

/**
 * @author one
 * @date 2020/04/12
 */
public class Client {
    @SneakyThrows
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(NioChannelOption.CONNECT_TIMEOUT_MILLIS, 10 * 1000);
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {

            SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
            sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            SslContext sslContext = sslContextBuilder.build();
            bootstrap.group(group);

            KeepaliveHandler keepaliveHandler = new KeepaliveHandler();
            LoggingHandler loggingHandler = new LoggingHandler(LogLevel.INFO);


            bootstrap.handler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("idleCheckHandler", new ClientIdleCheckHandler());
//                    pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));

                    // 编解码处理
                    pipeline.addLast(new OrderFrameDecoder());
                    pipeline.addLast(new OrderFrameEncoder());
                    pipeline.addLast(new OrderProtocolEncoder());
                    pipeline.addLast(new OrderProtocolDecoder());

                    pipeline.addLast("logHandler", loggingHandler);
                    pipeline.addLast("keepaliveHandler", keepaliveHandler);
                }
            });
            ChannelFuture channelFuture = bootstrap.connect("127.0.0.1", 8090);
            channelFuture.sync();
            AuthOperation authOperation = new AuthOperation("admin", "password");
            channelFuture.channel().writeAndFlush(new RequestMessage(IdUtil.nextId(), authOperation));
            RequestMessage requestMessage = new RequestMessage(IdUtil.nextId(), new OrderOperation(1001, "tudou"));
            channelFuture.channel().writeAndFlush(requestMessage);
            channelFuture.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}

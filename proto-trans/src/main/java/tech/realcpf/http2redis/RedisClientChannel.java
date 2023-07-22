package tech.realcpf.http2redis;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedisClientChannel {
    static Channel channel;
    static Queue<Channel> queue = new ConcurrentLinkedQueue<>();
    public void write(ByteBuf buf, Channel c){
        queue.add(c);
        channel.writeAndFlush(buf);
    }
    public RedisClientChannel(){
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ByteToMessageDecoder() {
                            @Override
                            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                                in.readerIndex(in.readableBytes());
                                ReferenceCountUtil.retain(in);
                                out.add(in);
                            }
                        });
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                msg.readerIndex(0);
                                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                response.content().writeBytes(msg);
                                Channel c = queue.poll();
                                if (c != null) {
                                    c.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                }
                                ctx.fireChannelRead(ReferenceCountUtil.retain(response,1));
                            }
                        });
                    }
                });
        try {
            channel =  b.connect("localhost",6379).sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}

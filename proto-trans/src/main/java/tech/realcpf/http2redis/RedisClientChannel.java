package tech.realcpf.http2redis;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.redis.*;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;
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

                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new RedisDecoder());
                        pipeline.addLast(new RedisBulkStringAggregator());
                        pipeline.addLast(new RedisArrayAggregator());
                        pipeline.addLast(new MessageToMessageDecoder<RedisMessage>() {
                            @Override
                            protected void decode(ChannelHandlerContext ctx, RedisMessage msg, List<Object> out) throws Exception {
                                if (msg instanceof SimpleStringRedisMessage) {
                                    SimpleStringRedisMessage simpleStringRedisMessage = (SimpleStringRedisMessage) msg;
                                    ByteBuf buf = ctx.alloc().buffer();
                                    buf.writeCharSequence(simpleStringRedisMessage.content(), StandardCharsets.UTF_8);
                                    out.add(buf);
                                }else if(msg instanceof FullBulkStringRedisMessage) {
                                    FullBulkStringRedisMessage message = (FullBulkStringRedisMessage)msg;
                                    out.add(message.content());
                                }
                                ReferenceCountUtil.retain(msg);
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
                                ctx.fireChannelRead(ReferenceCountUtil.retain(msg,1));
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

package tech.realcpf.redis2http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.redis.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class HttpClientChannel {
    public static Channel channel;
    static Queue<Channel> queue = new ConcurrentLinkedQueue<>();
    public void write(Channel c,Object msg) {
        queue.add(c);
        channel.writeAndFlush(msg);
    }
    public HttpClientChannel() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {

                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(1048576));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                                ByteBuf content = msg.content();
                                if (content.isReadable()) {
                                    int contentLength = content.readableBytes();
                                    byte[] arr = new byte[contentLength];
                                    content.readBytes(arr);
                                    SimpleStringRedisMessage simpleStringRedisMessage = new SimpleStringRedisMessage(new String(arr, 0, contentLength, CharsetUtil.UTF_8));
                                    Channel c = queue.poll();
                                    if (c != null) {
                                        c.writeAndFlush(simpleStringRedisMessage).addListener(ChannelFutureListener.CLOSE);
                                    }

                                }
                                ctx.fireChannelRead(ReferenceCountUtil.retain(msg, 1));
                            }
                        });
                    }
                });
        try {
            channel = b.connect("localhost", 8080).sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        HttpClientChannel client = new HttpClientChannel();
        ByteBuf buf = HttpClientChannel.channel.alloc().buffer();
        buf.writeCharSequence("b",StandardCharsets.UTF_8);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,"a");
        request.headers().add(HttpHeaderNames.HOST, "localhost");
        request.headers().add(HttpHeaderNames.CONTENT_TYPE,"application/x-www-form-urlencoded");
        request.headers().add(HttpHeaderNames.CONTENT_LENGTH,buf.readableBytes());
        request.content().writeCharSequence("b", StandardCharsets.UTF_8);
        client.write(null,request);
        TimeUnit.SECONDS.sleep(100);
    }

}

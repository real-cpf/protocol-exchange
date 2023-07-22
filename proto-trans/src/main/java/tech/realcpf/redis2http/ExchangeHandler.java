package tech.realcpf.redis2http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.redis.InlineCommandRedisMessage;
import io.netty.handler.codec.redis.RedisMessage;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import io.netty.util.ReferenceCountUtil;
import tech.realcpf.core.ExchangeHandlers;

import java.nio.charset.StandardCharsets;

public class ExchangeHandler  extends SimpleChannelInboundHandler<Object> implements ExchangeHandlers {

    private final HttpClientChannel httpClientChannel;
    public ExchangeHandler(HttpClientChannel channel){
        this.httpClientChannel = channel;
    }
    @Override
    public boolean doExchange(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof RedisMessage) {
            if (msg instanceof InlineCommandRedisMessage) {
                String line = ((InlineCommandRedisMessage) msg).content();
                String[] lines = line.split("\\s+");
                if ("set".equalsIgnoreCase(lines[0].trim())) {
                    DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,lines[1]);
                    request.content().writeCharSequence(lines[2], StandardCharsets.UTF_8);
                    request.headers().add(HttpHeaderNames.HOST, "localhost");
                    request.headers().add(HttpHeaderNames.CONTENT_TYPE,"application/x-www-form-urlencoded");
                    request.headers().add(HttpHeaderNames.CONTENT_LENGTH,request.content().readableBytes());
                    httpClientChannel.write(ctx.channel(),request);
                    return false;
                }
            } else if(msg instanceof SimpleStringRedisMessage){
                System.out.println(((SimpleStringRedisMessage) msg).content());
            }else {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (doExchange(ctx,msg)) return;
        ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
    }
}

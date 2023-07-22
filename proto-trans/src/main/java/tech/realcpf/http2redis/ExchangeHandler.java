package tech.realcpf.http2redis;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import tech.realcpf.core.ExchangeHandlers;

import java.nio.charset.StandardCharsets;

public class ExchangeHandler extends SimpleChannelInboundHandler<Object> implements ExchangeHandlers {
    private final RedisClientChannel redisChannel;
    public ExchangeHandler(RedisClientChannel redisClientChannel){
        redisChannel = redisClientChannel;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (doExchange(ctx,msg)) return;
        ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
    }

    @Override
    public boolean doExchange(ChannelHandlerContext ctx, Object msg) {
        String method = "";
        String uri = "";
        ByteBuf content = null;

        if (msg instanceof HttpRequest) {
            method = ((HttpRequest) msg).method().name();
            uri = ((HttpRequest) msg).uri().substring(1);
            if (msg instanceof HttpContent) {
                content = ((HttpContent) msg).content();
            }
        } else {
            ctx.fireChannelReadComplete();
            ctx.close();
            return true;
        }
        ByteBuf buf = ctx.alloc().buffer();
        switch (method) {
            case "GET":{
                buf.writeCharSequence("get ", StandardCharsets.UTF_8);
                buf.writeCharSequence(uri,StandardCharsets.UTF_8);
            }break;
            case "DELETE" :{
                buf.writeCharSequence("del ",StandardCharsets.UTF_8);
                buf.writeCharSequence(uri,StandardCharsets.UTF_8);
            }break;
            case "PUT":
            case "POST":{
                buf.writeCharSequence("set ",StandardCharsets.UTF_8);
                buf.writeCharSequence(uri,StandardCharsets.UTF_8);
                buf.writeCharSequence(" '",StandardCharsets.UTF_8);
                buf.writeBytes(content);
                buf.writeByte('\'');
            }break;
        }
        if (buf.writerIndex() > 0){
            buf.writeByte('\n');
            redisChannel.write(buf, ctx.channel());
        }
        return false;
    }
}

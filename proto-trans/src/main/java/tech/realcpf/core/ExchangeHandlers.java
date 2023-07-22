package tech.realcpf.core;

import io.netty.channel.ChannelHandlerContext;

public interface ExchangeHandlers {
    boolean doExchange(ChannelHandlerContext ctx,Object msg);
}

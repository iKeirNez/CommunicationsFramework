package com.iKeirNez.packetapi.implementation.handlers;

import com.iKeirNez.packetapi.api.HookType;
import com.iKeirNez.packetapi.api.connection.ClientConnection;
import com.iKeirNez.packetapi.implementation.standard.connection.IConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.TimeUnit;

/**
 * Created by iKeirNez on 19/04/2014.
 */
public class ReconnectHandler extends SimpleChannelInboundHandler<Object> {

    private final IConnection connection;

    public ReconnectHandler(IConnection connection){
        this.connection = connection;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // discard
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        if (!connection.closing.get()){
            if (connection instanceof ClientConnection){
                connection.logger.warning("Lost connection, attempting reconnect...");
                ctx.channel().eventLoop().schedule(((ClientConnection) connection)::connect, 5, TimeUnit.SECONDS);
            } else {
                connection.logger.warning("Lost connection, listening for reconnect...");
            }

            connection.getConnectionManager().callHook(connection, HookType.LOST_CONNECTION);
        }

        connection.closing.set(false);
    }
}

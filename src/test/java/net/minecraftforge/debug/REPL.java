package net.minecraftforge.debug;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import net.minecraftforge.common.animation.ITimeValue;
import net.minecraftforge.common.interpreter.AST.ISExp;
import net.minecraftforge.common.interpreter.AST.SExpTypeAdapterFactory;
import net.minecraftforge.common.interpreter.Interpreter;
import net.minecraftforge.common.animation.TimeValues;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class REPL
{
    public static void main(String[] args) throws Exception
    {
        ITimeValue dummyValue = new TimeValues.VariableValue(5);
        final Function<Object, ITimeValue> getter = Functions.constant(dummyValue);

        final Interpreter repl = new Interpreter(getter);

        final Gson gson = new GsonBuilder().registerTypeAdapterFactory(SExpTypeAdapterFactory.INSTANCE).create();

        final StringDecoder decoder = new StringDecoder(Charsets.UTF_8);
        final StringEncoder encoder = new StringEncoder(Charsets.UTF_8);

        @ChannelHandler.Sharable
        class Handler extends SimpleChannelInboundHandler<String>
        {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String input) throws Exception
            {
                if(input.equals("quit"))
                {
                    ChannelFuture future = ctx.writeAndFlush("o/\r\n");
                    future.addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                if(!input.isEmpty()) try
                {
                    System.out.println("input: " + input);
                    ISExp exp = gson.fromJson(input, ISExp.class);
                    ISExp result = repl.eval(exp);
                    ctx.writeAndFlush(result.toString() + "\r\n");
                }
                catch(Exception e)
                {
                    ctx.writeAndFlush(ExceptionUtils.getStackTrace(e));
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx)
            {
                ctx.flush();
            }
        }

        final Handler handler = new Handler();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try
        {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel channel) throws Exception
                    {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                        pipeline.addLast(decoder);
                        pipeline.addLast(encoder);
                        pipeline.addLast(handler);
                }
            });
            bootstrap.bind(16384).sync().channel().closeFuture().sync();
        }
        finally
        {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

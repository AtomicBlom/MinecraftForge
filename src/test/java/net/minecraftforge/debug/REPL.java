package net.minecraftforge.debug;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
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
import joptsimple.internal.Strings;
import net.minecraftforge.common.interpreter.AST;
import net.minecraftforge.common.interpreter.Interpreter;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.FileReader;
import java.util.Arrays;
import java.util.Map;

public class REPL
{
    private static final String PS = "> ";

    public static void main(String[] args) throws Exception
    {
        final Map<AST.ISExp, AST.ISExp> parameters = Maps.newHashMap();

        final Interpreter repl = new Interpreter();

        final Gson gson = new GsonBuilder().registerTypeAdapterFactory(AST.SExpTypeAdapterFactory.INSTANCE).create();

        final StringDecoder decoder = new StringDecoder(Charsets.UTF_8);
        final StringEncoder encoder = new StringEncoder(Charsets.UTF_8);

        @ChannelHandler.Sharable
        class Handler extends SimpleChannelInboundHandler<String>
        {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception
            {
                ctx.writeAndFlush("REPL started\r\n" + PS);
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String input) throws Exception
            {
                if(input.equals("quit"))
                {
                    ChannelFuture future = ctx.writeAndFlush("o/\r\n");
                    future.addListener(ChannelFutureListener.CLOSE);
                    return;
                }
                try
                {
                    if (input.startsWith("set "))
                    {
                        String[] parts = input.split(" +");
                        parameters.put(AST.makeSymbol(parts[1]), AST.makeFloat(Float.parseFloat(parts[2])));
                    }
                    else if (!input.isEmpty())
                    {
                        System.out.println("input: " + input);
                        AST.ISExp exp;
                        if (input.startsWith("eval "))
                        {
                            String name = input.substring("eval ".length());
                            exp = gson.fromJson(new FileReader(name), AST.ISExp.class);
                        }
                        else
                        {
                            exp = gson.fromJson(input, AST.ISExp.class);
                        }
                        AST.ISExp result = repl.eval(exp, ImmutableMap.copyOf(parameters));
                        ctx.write(result.toString() + "\r\n");
                    }
                }
                catch(Exception e)
                {
                    String[] rootCauseStackTrace = ExceptionUtils.getRootCauseStackTrace(e);
                    int stackTraceStart  = (rootCauseStackTrace.length - 1);
                    for (; stackTraceStart >= 0 ; --stackTraceStart) {
                        if (rootCauseStackTrace[stackTraceStart].contains("REPL$1Handler.channelRead0")) {
                            stackTraceStart--;
                            break;
                        }
                    }

                    if (stackTraceStart == -1) {
                        stackTraceStart  = (rootCauseStackTrace.length - 1);
                    }

                    String[] strings = Arrays.copyOfRange(rootCauseStackTrace, 0, stackTraceStart);

                    ctx.write(Strings.join(strings, "\r\n"));
                    ctx.write("\r\n\r\n");
                }
                ctx.writeAndFlush(PS);
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

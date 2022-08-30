/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

class MessagingProtocolV3Test {

  @Test
  void shouldCorrectEncodeDecodeProtocolRequest() throws Exception {
    // given
    final Address localhost = Address.from("localhost", 123);
    final MessagingProtocol v3Protocol = ProtocolVersion.V3.createProtocol(localhost);
    final ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
    when(channelHandlerContext.alloc()).thenReturn(ByteBufAllocator.DEFAULT);
    final AtomicReference<ByteBuf> bufAtomicReference = new AtomicReference<>();
    final List<ProtocolRequest> protocolRequests = new ArrayList<>();
    when(channelHandlerContext.write(any(ByteBuf.class), any()))
        .then(
            invocationOnMock -> {
              bufAtomicReference.set(invocationOnMock.getArgument(0));
              return invocationOnMock.getArgument(1);
            });
    when(channelHandlerContext.fireChannelRead(any()))
        .then(
            invocationOnMock -> {
              protocolRequests.add(invocationOnMock.getArgument(0));
              return invocationOnMock.getMock();
            });
    final ProtocolRequest initialProtocolRequest =
        new ProtocolRequest(
            1,
            localhost,
            "subject",
            "hello-world".getBytes(StandardCharsets.UTF_8),
            Map.of("testy-test", "long.long.long.long.long.long.long.long.long.long"));

    // when
    v3Protocol
        .newEncoder()
        .write(channelHandlerContext, initialProtocolRequest, mock(ChannelPromise.class));

    // then
    assertThat(bufAtomicReference)
        .hasValueMatching(byteBuf -> byteBuf.readerIndex() == 0)
        .hasValueMatching(byteBuf -> byteBuf.writerIndex() == 107);

    final ByteToMessageDecoder byteToMessageDecoder = v3Protocol.newDecoder();
    byteToMessageDecoder.channelRead(channelHandlerContext, bufAtomicReference.get());

    assertThat(protocolRequests)
        .hasSize(1)
        .anyMatch(protocolRequest -> protocolRequest.equals(initialProtocolRequest));
  }

  @Test
  void shouldCorrectEncodeDecodeProtocolReply() throws Exception {
    // given
    final Address localhost = Address.from("localhost", 123);
    final MessagingProtocol v3Protocol = ProtocolVersion.V3.createProtocol(localhost);
    final ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
    when(channelHandlerContext.alloc()).thenReturn(ByteBufAllocator.DEFAULT);
    final AtomicReference<ByteBuf> bufAtomicReference = new AtomicReference<>();
    final List<ProtocolReply> protocolReplies = new ArrayList<>();
    when(channelHandlerContext.write(any(ByteBuf.class), any()))
        .then(
            invocationOnMock -> {
              bufAtomicReference.set(invocationOnMock.getArgument(0));
              return invocationOnMock.getArgument(1);
            });
    when(channelHandlerContext.fireChannelRead(any()))
        .then(
            invocationOnMock -> {
              protocolReplies.add(invocationOnMock.getArgument(0));
              return invocationOnMock.getMock();
            });
    final ProtocolReply initialProtocolReply =
        new ProtocolReply(
            1,
            "hello-world".getBytes(StandardCharsets.UTF_8),
            Status.OK,
            Map.of("testy-test", "long.long.long.long.long.long.long.long.long.long"));

    // when
    v3Protocol
        .newEncoder()
        .write(channelHandlerContext, initialProtocolReply, mock(ChannelPromise.class));

    // then
    assertThat(bufAtomicReference)
        .hasValueMatching(byteBuf -> byteBuf.readerIndex() == 0)
        .hasValueMatching(byteBuf -> byteBuf.writerIndex() == 99);

    final ByteToMessageDecoder byteToMessageDecoder = v3Protocol.newDecoder();
    byteToMessageDecoder.channelRead(channelHandlerContext, bufAtomicReference.get());

    assertThat(protocolReplies)
        .hasSize(1)
        .anyMatch(protocolReply -> protocolReply.equals(initialProtocolReply));
  }

  @Test
  void shouldDecodeSplitedProtocolRequest() throws InterruptedException {
    final InetSocketAddress socketAddress = SocketUtil.getNextAddress();
    final Address localAddress =
        new Address(socketAddress.getHostString(), socketAddress.getPort());
    final MessagingProtocol v3Protocol = ProtocolVersion.V3.createProtocol(localAddress);

    Channel channel = null;
    Channel clientChannel = null;
    final Class<? extends ServerChannel> serverChannelClass;
    final Class<? extends Channel> channelClass;
    final EventLoopGroup eventLoopGroup;
    if (KQueue.isAvailable()) {
      eventLoopGroup = new KQueueEventLoopGroup();
      serverChannelClass = KQueueServerSocketChannel.class;
      channelClass = KQueueSocketChannel.class;
    } else if (Epoll.isAvailable()) {
      eventLoopGroup = new EpollEventLoopGroup();
      serverChannelClass = EpollServerSocketChannel.class;
      channelClass = EpollSocketChannel.class;
    } else {
      eventLoopGroup = new NioEventLoopGroup();
      serverChannelClass = NioServerSocketChannel.class;
      channelClass = NioSocketChannel.class;
    }
    try {
      final SaveToListHandler saveToListHandler = new SaveToListHandler();
      final int maxBufferSize = 100;
      channel =
          new ServerBootstrap()
              .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(maxBufferSize))
              .childOption(
                  ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(maxBufferSize))
              .group(eventLoopGroup)
              .channel(serverChannelClass)
              .childHandler(
                  new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                      ch.pipeline().remove(this);
                      ch.pipeline().addLast("decoder", v3Protocol.newDecoder());
                      ch.pipeline().addLast("handler", saveToListHandler);
                    }
                  })
              .bind(socketAddress)
              .channel();

      channel.read();
      clientChannel =
          new Bootstrap()
              .group(eventLoopGroup)
              .channel(channelClass)
              .handler(v3Protocol.newEncoder())
              .connect(socketAddress)
              .channel();
      final byte[] payload =
          RandomStringUtils.randomAlphabetic(40).getBytes(StandardCharsets.UTF_8);
      final ProtocolRequest protocolRequest =
          new ProtocolRequest(
              1,
              localAddress,
              "test-subject",
              payload,
              Map.of("testy-test", RandomStringUtils.randomAlphabetic(maxBufferSize * 2 / 3)));
      clientChannel.writeAndFlush(protocolRequest).await();
      assertThat(saveToListHandler.getRequests()).contains(protocolRequest);
    } finally {
      if (channel != null) {
        channel.close();
      }
      if (clientChannel != null) {
        clientChannel.close();
      }
    }
  }

  public static class SaveToListHandler extends SimpleChannelInboundHandler<ProtocolRequest> {

    private final List<ProtocolRequest> requests = new ArrayList<>();

    public List<ProtocolRequest> getRequests() {
      return requests;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ProtocolRequest msg) {
      requests.add(msg);
    }
  }
}

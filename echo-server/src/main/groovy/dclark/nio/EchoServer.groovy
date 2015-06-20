package dclark.nio;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import groovy.transform.TypeChecked;

@TypeChecked
class EchoServer implements Runnable {

    public static final int TIMEOUT = 5 * 1000;
    public static final int BUFFERSIZE = 8192;

    private final ServerSocketChannel serverChannel;

    public EchoServer(int port) {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(port));
    }

    public void run() {
        try {
            Selector selector = Selector.open();
            serverChannel.register(selector, serverChannel.validOps());
            while(selector.keys().size() > 0) {
                int keyCount = selector.select(TIMEOUT); //blocking step
                Iterator<SelectionKey> keysIter = selector.selectedKeys().iterator();
                while(keysIter.hasNext()) {
                    SelectionKey key = keysIter.next();
                    keysIter.remove();
                    if(!key.valid) {
                        continue;
                    }

                    if(key.acceptable) {
                        acceptable(key);
                    }

                    if(key.readable) {
                        readable(key);
                    }

                    if(key.writable) {
                        writable(key);
                    }
                }
            }
        }
        catch(IOException ioe) {
            //exit loop
        }
    }

    public void acceptable(SelectionKey key) {
        ServerSocketChannel srvChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = srvChannel.accept();
        channel.configureBlocking(false);
        channel.register(key.selector(), SelectionKey.OP_READ,
                         ByteBuffer.allocateDirect(BUFFERSIZE));
    }

    public void readable(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        int count = channel.read(buffer);
        if(count < 0) {
            while(buffer.position() > 0) {
                buffer.flip();
                channel.write(buffer);
                buffer.compact();
            }

            key.cancel();
            channel.close();
        }

        writable(key);
    }

    public void writable(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.flip();
        int count = channel.write(buffer);
        buffer.compact();
        int ops = key.interestOps();
        if(buffer.hasRemaining()) {
            ops |= SelectionKey.OP_WRITE;
        }
        else {
            ops &= ~SelectionKey.OP_WRITE;
        }

        key.interestOps(ops);
    }
}

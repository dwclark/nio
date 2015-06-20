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
    private final Selector selector;

    public EchoServer(int port) {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.socket().bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void run() {
        try {
            serverChannel.register(selector, serverChannel.validOps());
            while(selector.keys().size() > 0) {
                int keyCount = selector.select(TIMEOUT); //blocking step
                Iterator<SelectionKey> keysIter = selector.selectedKeys().iterator();
                while(keysIter.hasNext()) {
                    SelectionKey key = keysIter.next();
                    keysIter.remove();

                    if(key.valid && key.acceptable) {
                        onAccept(key);
                    }

                    if(key.valid && key.readable) {
                        onRead(key);
                    }

                    if(key.valid && key.writable) {
                        onWrite(key);
                    }
                }
            }
        }
        catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void onAccept(SelectionKey key) {
        ServerSocketChannel srvChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = srvChannel.accept();
        channel.configureBlocking(false);
        channel.register(key.selector(), SelectionKey.OP_READ,
                         ByteBuffer.allocateDirect(BUFFERSIZE));
    }

    public void onRead(SelectionKey key) {
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
            return;
        }

        onWrite(key);
    }

    public void onWrite(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.flip();
        int count = channel.write(buffer);
        buffer.compact();
        if(buffer.hasRemaining()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
        else {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    public static void main(String[] args) {
        new EchoServer(10_000).run();
    }
}

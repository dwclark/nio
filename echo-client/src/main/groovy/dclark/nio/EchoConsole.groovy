package dclark.nio;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.nio.charset.*;
import groovy.transform.TypeChecked;

@TypeChecked
class EchoConsole implements Runnable {

    private static final int TIMEOUT = 5_000;
    private static final int BUFFERSIZE = 8192;
    private static Charset ENCODING = Charset.forName('UTF-8');
    
    final SocketChannel channel;
    final Selector selector;
    final ByteBuffer buffer;
    final int port;
    final Console console;
    private int toRead = 0;
    
    public EchoConsole(int port) {
        channel = SocketChannel.open();
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        selector = Selector.open();
        buffer = ByteBuffer.allocate(BUFFERSIZE);
        this.port = port;
        console = System.console();
    }

    public void run() {
        channel.connect(new InetSocketAddress("127.0.0.1", port));
        channel.configureBlocking(false);

        String toWrite = null;
        while((toWrite = readInput())) {
            buffer.clear();
            byte[] bytes = toWrite.getBytes(ENCODING);
            toRead = bytes.length;
            buffer.put(bytes);
            channel.register(selector, SelectionKey.OP_WRITE, buffer);
            ioLoop();
        }
    }

    public void ioLoop() {
        while(selector.keys().size() > 0) {
            selector.select(TIMEOUT);
            Iterator<SelectionKey> keysIter = selector.selectedKeys().iterator();
            while(keysIter.hasNext()) {
                SelectionKey key = keysIter.next();
                keysIter.remove();
                
                if(!key.valid) {
                    continue;
                }
                
                if(key.readable) {
                    key.interestOps(onRead(key));
                }
                
                if(key.writable) {
                    key.interestOps(onWrite(key));
                }
                
                if(!key.interestOps()) {
                    console.printf("Leaving loop, but keeping channel registered.\n");
                    return;
                }
            }
        }
    }

    public int onRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        channel.read(buffer);
        
        if(buffer.position() < toRead) {
            return SelectionKey.OP_READ; //leave read selected
        }

        buffer.flip();
        byte[] bytes = new byte[toRead];
        buffer.get(bytes);
        
        console.printf("Read back: %s\n", new String(bytes, ENCODING));
        buffer.clear();
        
        return 0;
    }

    public int onWrite(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.flip();
        channel.write(buffer);
        if(buffer.hasRemaining()) {
            buffer.compact();
            return SelectionKey.OP_WRITE;
        }

        buffer.clear();
        return SelectionKey.OP_READ;
    }

    public String readInput() {
        console.printf("Enter some text: (Blank to stop): ");
        return console.readLine().trim();
    }

    public static void main(String[] args) {
        new EchoConsole(10_000).run();
    }
}

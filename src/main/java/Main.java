import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static  void main(String[] args) throws IOException {
        System.out.println("Hello World!"); // Display the string.
        serve(3000);
    }

    public static void ServeBlocking(int port) throws IOException{
        final ServerSocket socket = new ServerSocket(port); // #1 Bind server to port
        try{
            while (true){
                final Socket clientSocket = socket.accept(); // #2 Block until new client connection is accepted
                System.out.println("Accepted connection from "+ clientSocket);

                new Thread(new Runnable() { // #3 Create new thread to handle client connection
                    @Override
                    public void run() {
                        try{
                            BufferedReader reader =  new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(),true);

                            while(true){ // #4 Read data from client and write it back
                                writer.println(reader.readLine());
                                writer.flush();
                            }
                        } catch (IOException e){
                            e.printStackTrace();
                            try{
                                clientSocket.close();
                            } catch (IOException ex){
                                //Ignore on close
                            }
                        }
                    }
                }).start(); // #5 Start thread
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }


    public static void ServeNIO(int port) throws IOException{
        System.out.println("Listening for connections on port " + port);
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        ServerSocket ss = serverChannel.socket();

        InetSocketAddress address = new InetSocketAddress(port);
        ss.bind(address); // #1 Bind server to port
        serverChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); // #2 Register the channel with the selector to be interested in new Client connections that get accepted

        while(true){
            try{
                selector.select(); //#3 Block until something is selected
            }catch (IOException ex){
                ex.printStackTrace();
                //handle in a proper way
                break;
            }

            Set readKeys = selector.selectedKeys(); // #4 Get all SelectedKey instances
            Iterator iterator = readKeys.iterator();
            while(iterator.hasNext()){
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove(); // #5 Remove the SelectedKey from the iterator
                try{
                    if(key.isAcceptable()){
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client =  server.accept(); // #6 Accept the client connection
                        System.out.println("Accepted connection from "+ client);
                        client.configureBlocking(false);
                        client.register(selector,SelectionKey.OP_WRITE| SelectionKey.OP_READ, ByteBuffer.allocate(100)); // #7 Register connection to selector and set ByteBuffer
                    }
                    if (key.isReadable()) { //#8 Check for SelectedKey for read
                        SocketChannel client = (SocketChannel)
                                key.channel();
                        ByteBuffer output = (ByteBuffer) key.attachment();
                        client.read(output);//#9 Read data to ByteBuffer
                    }

                    if(key.isReadable()){
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer output = (ByteBuffer) key.attachment();
                        output.flip();
                        client.write(output);
                        output.compact();
                    }

                    if (key.isWritable()) { //#10 Check for SelectedKey for write
                        SocketChannel client = (SocketChannel)
                                key.channel();
                        ByteBuffer output = (ByteBuffer) key.attachment();
                        output.flip();
                        client.write(output); //#11 Write data from ByteBuffer to channel
                        output.compact();
                    }
                }catch (IOException ex){
                    key.cancel();
                    try{
                        key.channel().close();
                    }catch (IOException cex){

                    }
                }
            }
        }
    }

    public static void serve(int port) throws IOException {
        System.out.println("Listening for connections on port " + port);
        final AsynchronousServerSocketChannel serverChannel =
                AsynchronousServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress(port);
        serverChannel.bind(address); //#1Bind Server to port
        final CountDownLatch latch = new CountDownLatch(1);
        serverChannel.accept(null, new
                CompletionHandler<AsynchronousSocketChannel, Object>() { //#2 Start to accept new Client connections. Once one is accepted the CompletionHandler will get called.
                    @Override
                    public void completed(final AsynchronousSocketChannel channel,
                                          Object attachment) {
                        serverChannel.accept(null, this); //#3 Again accept new Client connections
                        ByteBuffer buffer = ByteBuffer.allocate(100);
                        channel.read(buffer, buffer,
                                new EchoCompletionHandler(channel));//#4 Trigger a read operation on the Channel, the given CompletionHandler will be notified once something was read
                    }
                    @Override
                    public void failed(Throwable throwable, Object attachment) {
                        try {
                            serverChannel.close(); //#5 Close the socket on error
                        } catch (IOException e) {
// ingnore on close
                        } finally {
                            latch.countDown();
                        }
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    private static final class EchoCompletionHandler implements
            CompletionHandler<Integer, ByteBuffer> {
        private final AsynchronousSocketChannel channel;
        EchoCompletionHandler(AsynchronousSocketChannel channel) {
            this.channel = channel;
        }
        @Override
        public void completed(Integer result, ByteBuffer buffer) {
            buffer.flip();
            channel.write(buffer, buffer, new CompletionHandler<Integer,
                    ByteBuffer>() { //#6 Trigger a write operation on the Channel, the given CompletionHandler will be notified once  something was written
                @Override
                public void completed(Integer result, ByteBuffer buffer) {
                    if (buffer.hasRemaining()) {
                        channel.write(buffer, buffer, this); //#7 Trigger again a write operation if something is left in the ByteBuffer
                    } else {
                        buffer.compact();
                        channel.read(buffer, buffer,
                                EchoCompletionHandler.this); //#8 Trigger a read operation on the Channel, the given CompletionHandler will be notified once something was read
                    }
                }
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    try {
                        channel.close();
                    } catch (IOException e) {
// ingnore on close
                    }
                }
            });
        }
        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            try {
                channel.close();
            } catch (IOException e) {
// ingnore on close
            }
        }
    }
}

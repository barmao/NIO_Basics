import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static  void main(String[] args) throws IOException {
        System.out.println("Hello World!"); // Display the string.
        ServeBlocking(3000);
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
}

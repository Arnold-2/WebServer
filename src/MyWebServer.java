
import java.io.*;

// Networking libraries

import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;

class Worker extends Thread { // extending Thread class so workers can run concurrently
    Socket sock; // a socket is endpoint for communication

    // Worker constructor. Initialize local variable
    Worker (Socket s) {
        this.sock = s;
    }

    // Main functionality to execute when thread starts
    public void run() {
        PrintStream out = null;

        try {
            // Create objects Reading and Writing data from the socket

            // Log and print input request
            ServerLog(sock);

            // **Socket.getOutputStream() returns an OutputStream object that writes bytes to the socket
            // **PrintStream prints data to the output stream
            out = new PrintStream(sock.getOutputStream());

            // Send response back to client
            out.println("<MyWebServer> Request received: " + LocalDate.now() + " " + LocalTime.now());

            // close the socket after use
            CloseConnection(sock);
        } catch (IOException iox){
            System.out.println(iox);
            CloseConnection(sock);
        }
    }

    public void CloseConnection(Socket s){
        try{
            s.close();
        } catch (IOException x){
            x.printStackTrace();
        }
    }


    private void ServerLog(Socket s){
        BufferedReader in = null; // BufferedReader to read request
        String inText;
        String fileName = System.getProperty("user.dir") + "\\http-streams.txt";
        StringBuffer sb = new StringBuffer();

        try{
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            // Adding current server time for debugging
            sb.append("<Server Time>" + LocalDate.now() + " " + LocalTime.now() + ": \r\n");

            // Read one line from the client
            inText = in.readLine();

            // Read all lines
            while (inText.length() > 0){
                // Append to output
                sb.append(inText + "\r\n");

                // Move to next line
                inText = in.readLine();
            }

            // Print out to screen
            System.out.print(sb.toString());

            // Write to log file
            WriteToFile(fileName, sb.toString());

        } catch (IOException x){
            System.out.println("Server read error");
            x.printStackTrace();
            CloseConnection(sock);
        }
    }

    private void WriteToFile (String fileName, String content){
        File f = new File(fileName);

        if (!f.exists()){
            // Create a empty file
            try{ f.createNewFile(); } catch (IOException x){ x.printStackTrace(); }
        }

        try{
            // Open a new writer
            BufferedWriter fWriter = new BufferedWriter(new FileWriter(fileName));

            // Write content to file
            fWriter.write(content);

            fWriter.close();

        } catch(IOException x){
            x.printStackTrace();
        }

    }



}

public class MyWebServer {
    public static void main (String a[]) throws IOException{

        int q_len = 6; // backlog value for creating ServerSocket
        int port = 2540; // Hardcoded port listening to 2540
        Socket sock;

        // Create a server socket for clients to connect to
        ServerSocket servsock = new ServerSocket(port, q_len);

        System.out.println("MyWebServer v1.0, listening to port: " + port + ".\n");

        // Infinite loop to process client's request
        while (true) {
            // **Loop pauses here to wait client's request.
            // **Once got the request, pass the socket to the sock variable
            sock = servsock.accept();
            // **Once the sock populated, create a new worker thread and start it (Let it run and die)
            // **After the start, current loop will finish the iteration and go back to wait for next
            // ***The loop doesn't wait the worker to finish. That's the spirit of multithreading.
            new Worker(sock).start();
        }
    }
}

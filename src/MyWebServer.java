/*--------------------------------------------------------

1. Jilei Hao / Feb 13, 2019

2. JDK 1.8

3. Command line instructions

> javac MyWebServer.java
> java MyWebServer


4. Instructions to run this program:

> java MyWebServer

5. Note:

When parsing CGI requests, I faked the file name check part. The program will return
the Addnums page to user whenever the request contains "/cgi/". But requests contain
"AddNums.fake-cgi?" will be parsed as FORM request.

----------------------------------------------------------*/

import java.io.*;

// Networking libraries

import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;

class Worker extends Thread { // extending Thread class so workers can run concurrently
    Socket sock; // a socket is endpoint for communication
    String logFileName = System.getProperty("user.dir") + "\\http-streams.txt";
    ServerLogger slog = new ServerLogger(logFileName); // server logger to log events to console or to file
    // Assemble final path
    private final String root = System.getProperty("user.dir");

    // Worker constructor. Initialize local variable
    Worker (Socket s) {
        this.sock = s;
    }

    // Main functionality to execute when thread starts
    public void run() {
        try {
            // Create reader
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String reqText;

            // Get output stream
            PrintStream out = new PrintStream(sock.getOutputStream());

            // Parsing GET request
            // --Get first line from the request
            reqText = in.readLine();

            // filter out empty request and parent directory requests
            if (reqText == null || reqText.indexOf("..") >= 0){
                CloseConnection();
                return;
            }

            //System.out.println("<raw>" + reqText);

            // --If invalid, return 400 Bad request
            if (!reqText.substring(0, 3).equals("GET")){
                out.println("HTTP 400: Not a GET request");
                CloseConnection();
                return;
            }

            if (reqText.indexOf("HTTP") < 0){
                out.println("HTTP 400: Not a HTTP request");
                CloseConnection();
                return;
            }

            // Parse requested file path
            // substring, length = 2nd space - 1st space
            String reqFilePath = reqText.substring(reqText.indexOf(" ")
                    ,reqText.indexOf(" ", reqText.indexOf(" ") + 1));

            slog.appendln(getTimeHdr());
            slog.appendln("<Request> " + reqText);
            slog.appendln("<File Path> " + reqFilePath);

            // Ignore empty request
            if (reqFilePath.trim().length() <= 1){
                out.println("MyWebServer v1.0");
                CloseConnection();
                return;
            }

            // Ignore /favicon.ico
            // -- Print out on console
            // -- send 204 NO CONTENT back to client

            if (reqFilePath.indexOf("/favicon.ico") >= 0){
                slog.appendln("<Server> Ignoring /FAVICON.ICO request");
                slog.flushToFile();
                out.println("HTTP/1.1 204 NO CONTENT");
                CloseConnection();
                return;
            }

            StringBuilder response = new StringBuilder();

            // process request
            // --decide request type
            // ----directory
            if (reqFilePath.indexOf(".") < 0 || reqFilePath.indexOf("./") >= 0 || reqFilePath.trim().endsWith("/")){
                slog.appendln("<Server> Processing Dir Request...");
                ProcessDirRequest(reqFilePath, response);
            }
            // ----invalid file path
            else if (reqFilePath.indexOf(".") < 0) {
                ProcessBadRequest(response);
                out.print(response.toString());
                CloseConnection();
            }
            // ---- file
            // ---- fake cgi handler
            else if (reqFilePath.indexOf("/cgi/") >= 0){
                slog.appendln("<Server> Processing CGI Request...");
                ProcessCGIRequest(reqFilePath, response);
            }
            else{
                slog.appendln("<Server> Processing File Request...");
                ProcessFileRequest(reqFilePath, response);
            }


            // send response
            out.print(response.toString());
            out.flush();

            // Log and print input request
            slog.flushToFile();

            // close the socket after use
            CloseConnection();
        } catch (IOException iox){
            iox.printStackTrace();
            CloseConnection();
        }
    }

    public void CloseConnection(){
        try{
            this.sock.close();
        } catch (IOException x){
            x.printStackTrace();
        }
    }

    private String getTimeHdr(){
        return "-------------------------------------------------------------\r\n"
                + "<Server Time>" + LocalDate.now() + " " + LocalTime.now() + "\r\n"
                + "-------------------------------------------------------------\r\n";
    }

    // Generate HTML listing all files under given path
    private void ProcessDirRequest(String path, StringBuilder rep){
        // Root path + requested relative path
        String relativePath = path.trim().replace("/", "\\")
                + (path.trim().endsWith("/") ? "" : "/");

        String dir = root + (path.trim().equals("/::/") ? "" : relativePath);

        //System.out.println("Dir: " + dir);

        // Generate parent directory
        String parentDir;

        // Remove the last leg of the relative Directory
        if (path.endsWith("/"))
            parentDir = path.trim().substring(0, path.lastIndexOf("/", path.trim().length() - 1));
        else
            parentDir = path.trim().substring(0, path.lastIndexOf("/"));

        //System.out.println("ParentDir: " + parentDir);

        if (parentDir.trim().equals("/"))
            parentDir = "/::/"; // To get the root directory

        // Start building HTML response
        rep.append("HTTP/1.1 200 OK\r\n");
        rep.append("Content-Length: " + 666 + "\r\n"); // fake length here
        rep.append("Content-Type: text/html \r\n");
        // Two crlf before data
        rep.append("\r\n\r\n");
        // Head
        rep.append("<html><body><pre>\r\n");

        File startFile = new File(dir);

        // get all files & directories under startFile
        File[] dirList = startFile.listFiles();

        if (dirList == null){
            rep.append("Invalid Directory!\r\n </pre></body></html>\r\n");
            return;
        }

        // write header
        rep.append("<h1>Index of "
                + (path.trim().equals("/::/") ? "RootDirectory" : path.trim())
                + "</h1>");

        // write parent directory
        rep.append("<a href=\"" + parentDir + "\">Parent</a><br>\r\n");

        if (path.trim().equals("/::/"))
            path = "/";

        // print out directory
        for ( int i = 0 ; i < dirList.length ; i ++ ) {
            // Create a hyperlink element
            rep.append("<a href=\"");

            String t;
            // Populate the url
            // sample: <a href="/src/test1/">test1/</a>
            if ( dirList[i].isDirectory ( ) ){
                t = stripFullPath(dirList[i].toString(), true);
                rep.append(t + "\">" + t.substring(path.trim().length()) + "</a><br>\r\n");
            }

            else if ( dirList[i].isFile ( ) ){
                t = stripFullPath(dirList[i].toString(), false);
                rep.append(t + "\">" + t.substring(path.trim().length()) + "</a><br>\r\n");
            }
        }

        // close the document
        rep.append("</pre></body></html>\r\n");

        //System.out.println("<debug 1>\r\n" + rep.toString());
    }

    private String stripFullPath(String fullPath, boolean isDirectory){
        String ret = fullPath.replace(root, "").replace("\\", "/")
                + (isDirectory ? "/" : "");
        System.out.println(ret);
        return ret;
    }

    private void ProcessBadRequest(StringBuilder rep){
        rep.append("Invalid Request");
    }

    private void ProcessCGIRequest(String path, StringBuilder rep){
        // Write Header first
        rep.append("HTTP/1.1 200 OK\r\n");
        rep.append("Content-Length: " + 800 + "\r\n");
        rep.append("Content-Type: text/html \r\n");
        // Two crlf before data
        rep.append("\r\n\r\n");

        // Check if request was sent by the Form
        if (path.trim().toUpperCase().indexOf("ADDNUMS.FAKE-CGI?") >= 0){
            // If it's form request, parse parameters and call addNum
            String[] parmStr = path.trim().substring(path.trim().indexOf('?') + 1).split("&");
            String name = parmStr[0].trim().substring(parmStr[0].trim().indexOf('=') + 1);
            String num1 = parmStr[1].trim().substring(parmStr[1].trim().indexOf('=') + 1);
            String num2 = parmStr[2].trim().substring(parmStr[2].trim().indexOf('=') + 1);

            slog.appendln("<Parse FORM parms> Name: " + name + "; num1: " + num1 + "; num2: " + num2);

            // Call Addnums function
            int result = AddNums(Integer.parseInt(num1), Integer.parseInt(num2));

            // Prepare response
            rep.append("<html>\r\n");
            rep.append("<pre>\r\n");
            rep.append("Dear " + name + "! the sum of " + num1 + " and " + num2 + " is " + result + ".\r\n");
            rep.append("</pre></html>\r\n");
        } else{
            // If it's other cgi request, return the html form
            // Data
            rep.append("<html>\r\n");
            rep.append("<head><TITLE> CSC435 Sample Form for AddNum </TITLE></head>\r\n");
            rep.append("<BODY>\r\n <H1> Addnum </H1>\r\n");
            rep.append("<FORM method=\"GET\" action=\"http://localhost:2540/cgi/addnums.fake-cgi\">\r\n");
            rep.append("Enter your name and two numbers:\r\n");
            rep.append("<INPUT TYPE=\"text\" NAME=\"person\" size=20 value=\"YourName\"><P>\r\n");
            rep.append("<INPUT TYPE=\"text\" NAME=\"num1\" size=5 value=\"4\"> <br>\r\n");
            rep.append("<INPUT TYPE=\"text\" NAME=\"num2\" size=5 value=\"5\"> <br>\r\n");
            rep.append("<INPUT TYPE=\"submit\" VALUE=\"Submit Numbers\">\r\n");
            rep.append("</FORM> </BODY></html>\r\n");
        }

    }

    private int AddNums(int num1, int num2){
        return num1 + num2;
    }

    private void ProcessFileRequest(String path, StringBuilder rep){
        // --parse content type
        // --file type is the file extension user requested
        String fileType = path.substring(path.indexOf(".") + 1);
        // --content type is the MIME content type
        // ----only handle 2 types
        String contentType = fileType.trim().toUpperCase().equals("HTML") ? "text/html" : "text/plain";


        // Get content from the file
        StringBuilder fileContent = new StringBuilder();
        int ret = getFileContent(path.trim(), fileContent);

        //slog.append(fileContent);

        if (ret >= 0){
            rep.append("HTTP/1.1 200 OK\r\n");
            rep.append("Content-Length: " + fileContent.length() + "\r\n");
            rep.append("Content-Type: " + contentType + "\r\n");
            // Two crlf before data
            rep.append("\r\n\r\n");
            rep.append(fileContent.toString());

            slog.appendln("<Response>");
            slog.appendln(rep.toString());
        }
        else{
            rep.append("HTTP/1.1 204 NO CONTENT");
        }
    }

    private int getFileContent(String path, StringBuilder content){
        int ret = 0;
        String dir = root +  path.replace('/', '\\');

        slog.appendln("<Server> Retrieving data from file: " + dir);

        File f = new File (dir);

        if (f.exists()){

            try{
                // if file exists, start reading data
                BufferedReader rdr = new BufferedReader(new FileReader(f));

                StringBuilder sb = new StringBuilder();
                String tmp;
                while ((tmp = rdr.readLine())!= null){
                    sb.append(tmp);
                    sb.append("\r\n");
                }

                content.append(sb.toString());
            } catch (IOException x){
                slog.appendln("Failed reading file: " + x.toString());
                ret = -1;
                x.printStackTrace();
            }
        } else{
            slog.appendln("Failed: File does not exist!");
            ret = -1;
        }

        return ret;
    }
}

// Server logger helper class to help record and generate logs
// -- This class is broken. Don't take closer look. Very buggy.
class ServerLogger {
    private StringBuilder privSB;
    private String privFileName = null;

    // Create a screen logger
    ServerLogger(){
        privSB = new StringBuilder();
    }

    // Create a file logger
    ServerLogger(String logFileName){
        privSB = new StringBuilder();
        this.privFileName = logFileName;
    }

    // Append one line to log
    void appendln(String s){
        privSB.append(s + "\r\n");
        System.out.println(s);
    }

    // Append string to log
    void append(String s) {
        privSB.append(s);
    }

    // flush log to both console and file
    void flush(){
        System.out.print(privSB.toString());
        this.flushToFile();
    }

    // flush to console only
    void flushToConsole() {
        System.out.print(privSB.toString());
        this.reset();
    }

    // print out current log without reset
    void printCurrent() {
        System.out.print(privSB.toString());
    }

    // flush to file only
    void flushToFile() {
        if (privFileName == null)
            return;

        File f = new File(this.privFileName);

        if (!f.exists()){
            // Create a empty file
            try{ f.createNewFile(); } catch (IOException x){ x.printStackTrace(); }
        }

        try{
            // Open a new writer
            BufferedWriter fWriter = new BufferedWriter(new FileWriter(this.privFileName, true));

            // Write content to file
            fWriter.write(privSB.toString());

            fWriter.close();

        } catch(IOException x){
            x.printStackTrace();
        }

        this.reset();
    }

    // reset log string
    private void reset(){
        this.privSB = new StringBuilder();
    }

    // get current log content
    @Override
    public String toString() {
        return privSB.toString();
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

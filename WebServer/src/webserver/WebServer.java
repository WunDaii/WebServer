// The advanced feature we have attempted is "2.1. Support for client-side caching (conditional GET requests)"
package webserver;

import in2011.http.Request;
import in2011.http.Response;
import in2011.http.MessageFormatException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.http.client.utils.DateUtils;

public class WebServer {

    private int port;
    private String rootDir;

    public WebServer(int port, String rootDir) {
        this.port = port;
        this.rootDir = rootDir;
    }

    public void start() throws IOException {
        
        // create a server socket
        ServerSocket serverSock = new ServerSocket(port);
        while (true) {

            Socket conn = serverSock.accept();
            InputStream is = conn.getInputStream();
            OutputStream os = conn.getOutputStream();

            Response msg = new Response(500);

            try {
                
                Request req = Request.parse(is);
                String URI = req.getURI();
                String method = req.getMethod();

                if (!req.getVersion().equals("1.1")) {
                    msg = new Response(505);
                    msg.write(os);
                } else if (!method.equals("GET") && !method.equals("PUT")) {
                    msg = new Response(501);
                    msg.write(os);
                } else {

                    String ps = rootDir + URI;
                    Path path = Paths.get(ps).toAbsolutePath().normalize();
                    File file = path.toFile();

                    if (file.exists()) {

                        if (method.equals("GET")) {

                            if (req.getHeaderFieldValue("If-Modified-Since") == null) {
                                msg = new Response(200);

                                try (InputStream newIs = new BufferedInputStream(new FileInputStream(file))) {
                                    String contentType = URLConnection.guessContentTypeFromStream(newIs);
                                    msg = new Response(200);
                                    msg.addHeaderField("Content-Type: ", contentType);
                                    msg.addHeaderField("Content-Length: ", "" + file.length());
                                    DateFormat format = new SimpleDateFormat("dd/MM/yyyy");
                                    msg.addHeaderField("Last Modified: ", format.format(new Date(file.lastModified())));

                                    msg.write(os);
                                    os.write(Files.readAllBytes(path));
                                }

                            } else {
                                String dateString = req.getHeaderFieldValue("If-Modified-Since");
                                Date date = DateUtils.parseDate(dateString);
                                date = DateUtils.parseDate(DateUtils.formatDate(date));

                                String lastModifiedDateStr = DateUtils.formatDate(new Date(file.lastModified()));
                                Date lastModified = DateUtils.parseDate(lastModifiedDateStr);

                                if (lastModified.after(date)) {

                                    msg = new Response(200);

                                    try (InputStream newIs = new BufferedInputStream(new FileInputStream(file))) {
                                        String contentType = URLConnection.guessContentTypeFromStream(newIs);
                                        msg = new Response(200);
                                        msg.addHeaderField("Content-Type: ", contentType);
                                        msg.addHeaderField("Content-Length: ", "" + file.length());
                                        DateFormat format = new SimpleDateFormat("dd/MM/yyyy");
                                        msg.addHeaderField("Last Modified: ", format.format(new Date(file.lastModified())));

                                        msg.write(os);
                                        os.write(Files.readAllBytes(path));
                                    }

                                } else {
                                    msg = new Response(304);
                                    msg.write(os);
                                }
                            }

                        }
                    } else {
                        msg = new Response(404);
                        msg.write(os);
                    }
                }
            } catch (MessageFormatException ex) {
                msg = new Response(400);
                msg.write(os);
            }

            if (msg.getStatusCode() == 500) {
                msg.write(os);
            }

            // close the connection
            conn.close();
        }
    }

    public static void main(String[] args) throws IOException {
        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2) {
            throw new Error(usage);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Error(usage + "\n" + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        WebServer server = new WebServer(port, rootDir);
        server.start();
    }
}

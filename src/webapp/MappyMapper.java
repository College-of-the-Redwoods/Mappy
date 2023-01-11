package webapp;

import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

@ServerEndpoint(value = "/socket")
public class MappyMapper implements Runnable {

    private final String resultAddress;
    private static Set<Session> mapWindows;

    public MappyMapper() throws java.io.IOException {
        // Load config from config.ini file
        Ini ini = new Ini(new java.net.URL("http", "127.0.0.1", 8080, "/config.ini"));
        java.util.prefs.Preferences prefs = new IniPreferences(ini);
        // Instantiate set used to track map sessions.
        mapWindows = new CopyOnWriteArraySet<>();
        // Set ZMQ address used to get ZMQ messages.
        resultAddress = prefs.node("zmq").get("resultAddr", "");
        // Start ZMQ thread for receiving messages
        Thread thread = new Thread(this); // Put runnable worker in new thread
        thread.start(); // Start runnable thread
    }

    public void run() {
        // Connect to our job socket and sender socket.
        try (ZContext context = new ZContext()) {
            // Channel to receive mappy worker filtered jobs from
            ZMQ.Socket resultSocket = context.createSocket(SocketType.PULL);
            resultSocket.bind(resultAddress);

            while (!Thread.currentThread().isInterrupted()) {
                String mapMessage = resultSocket.recvStr();
                System.out.flush();
                System.out.println(mapMessage);
                // Place on MAP.
                mapWindows.forEach(session -> {
                    synchronized (session) {
                        try {
                            session.getBasicRemote().sendText(mapMessage);
                        } catch (IOException ioe) {
                            System.out.println("Error broadcasting message.");
                        }
                    }
                });
            }
        }
    }

    @OnOpen
    public void messageOpen(Session session) {
        //this.session = session;
        System.out.printf("New session %s%n", session.getId());
        mapWindows.add(session); // Add this new session to list of sessions.
    }

    @OnClose
    public void close(Session session) {
        mapWindows.remove(session); // Remove the session because the socket closed
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("There has been an error with session " + session.getId());
    }

    @OnMessage
    public String echoTextMessage(Session session, String message) { //TODO: Could use as a PING or keep-alive
        System.out.println("Websocket Ping Received.");
        return message;
    }
    @OnMessage(maxMessageSize = 1024000)
    public byte[] handleBinaryMessage(byte[] buffer) {
        System.out.println("New Binary Message Received");
        return buffer;
    }
}

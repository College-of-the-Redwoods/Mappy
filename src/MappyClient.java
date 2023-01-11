import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

import java.io.File;
import java.util.Scanner;

public class MappyClient {

    public static void main(String[] args) throws java.io.IOException {
        try (ZContext context = new ZContext()) {
            // Load config from config.ini file
            Ini ini = new Ini(new File("config.ini"));
            java.util.prefs.Preferences prefs = new IniPreferences(ini);

            // Channel to send address geocode jobs to.
            ZMQ.Socket workerSocket = context.createSocket(SocketType.PUSH);
            workerSocket.bind(
                    prefs.node("zmq").get("workAddr", ""));

            Scanner scanner = new Scanner(System.in);
            String address;
            do {
                System.out.print("Please enter the street address you wish to map or \"Q\" to Quit: ");
                address = scanner.nextLine();
                workerSocket.send(address, 0);
            } while(!address.equalsIgnoreCase("Q"));
        }
    }

}

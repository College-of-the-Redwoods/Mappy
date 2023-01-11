// Imports used for handling ZMQ
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
// Imports used for making rest calls
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
// Imports used for handling json
import com.google.gson.*;
import com.google.gson.JsonParser;
import java.util.SortedMap;
import java.util.TreeMap;
// Imports used for reading config values
import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import java.io.File;

public class MappyWorker implements Runnable {

    private final String workAddr, resultAddr, apiKey;

    public MappyWorker(String workAddr, String resultAddr, String apiKey) {
        this.workAddr = workAddr;
        this.resultAddr = resultAddr;
        this.apiKey = apiKey;
    }

    private JsonArray geoCode(String address) {
        // Using geoapify instead of OSM Nominatim API for faster, version controlled geocoding.
        // TODO: Also support Nominatim API as a first or second level lookup.
        //       https://nominatim.org/release-docs/latest/api/Overview/
        //       https://nominatim.openstreetmap.org/search?q=17+Strada+Pictor+Alexandru+Romano%2C+Bukarest&format=geojson
        JsonArray response = null;
        try {
            String urlPathAndQuerystring = "/v1/geocode/search?text="
                    + URLEncoder.encode(address, StandardCharsets.UTF_8)
                    + "&apiKey="
                    + this.apiKey;

            URL url = new URL("https", "api.geoapify.com", urlPathAndQuerystring);
            HttpURLConnection http = (HttpURLConnection)url.openConnection();
            http.setRequestProperty("Accept", "application/json");

            if(http.getResponseCode() == 200) {
                // TODO: Instead of brute force walk of Gson Json Object, create a Java class that mimics the response
                //       and loads it as indicated here:
                //       https://stackoverflow.com/questions/60109185/get-value-of-json-with-gson
                BufferedReader reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
                response = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("features").getAsJsonArray().get(0).getAsJsonObject()
                        .get("geometry").getAsJsonObject()
                        .get("coordinates").getAsJsonArray();
            }

            http.disconnect();
        } catch(java.io.UnsupportedEncodingException uee) {
            System.out.println("Could not encode address for geocoding.");
        } catch(java.io.IOException ioe) {
            System.out.println("Failed to retrieve geocoding information.");
        } catch(IndexOutOfBoundsException ioobe){
            System.out.println("Error parsing response json.");
        }

        // TODO: Make the return type more meaningful, not just an array string of lat/lng
        if( response != null) { return response; }
        return new JsonArray();
    }

    public void run() {
        try (ZContext context = new ZContext()) {
            // Connect to our job socket and sender socket.
            // Channel to receive mappy jobs from
            ZMQ.Socket jobSocket = context.createSocket(SocketType.PULL);
            jobSocket.connect(workAddr);
            // Channel to send mappy jobs to
            ZMQ.Socket senderSocket = context.createSocket(SocketType.PUSH);
            senderSocket.connect(resultAddr);

            while (!Thread.currentThread().isInterrupted()) {
                String address = jobSocket.recvStr();
                System.out.flush();
                System.out.println("Processing: " + address);
                // Do something to filter this job or process it.
                JsonArray coordinates = geoCode(address);
                if( coordinates.size() == 2) { // Dumb check to see if geoCode was successful
                    // TODO: Cache and lookup geo-codes from cache so we don't waste API calls.
                    //       if we KEY by coordinates, we can avoid duplicates.
                    String id = SwissArmyKnife.getMd5(
                            coordinates.get(0).getAsString()+"-"+coordinates.get(1).getAsString());
                    // Build a map that we'll convert to a json message.
                    SortedMap<String, Object> message = new TreeMap<>();
                    message.put("id", id);
                    message.put("address", address);
                    message.put("coordinates", coordinates);

                    senderSocket.send(
                            SwissArmyKnife.convertMapToJson(message), 0
                    );
                }
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
        // Load config from config.ini file
        Ini ini = new Ini(new File("config.ini"));
        java.util.prefs.Preferences prefs = new IniPreferences(ini);

        MappyWorker worker = new MappyWorker(
                prefs.node("zmq").get("workAddr", ""),
                prefs.node("zmq").get("resultAddr", ""),
                prefs.node("geoapify").get("apiKey", ""));
        Thread thread = new Thread(worker); // Put runnable worker in new thread
        thread.start(); // Start runnable thread
    }
}

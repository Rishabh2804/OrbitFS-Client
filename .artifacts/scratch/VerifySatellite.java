import org.orbitfs.client.NetworkTransportClient;
import org.orbitfs.client.CachingOrbitFSClient;
import org.orbitfs.common.protocol.RPCResponse.RPCEntry;
import java.util.List;

public class VerifySatellite {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 9090;
        
        System.out.println("Connecting to Satellite at " + host + ":" + port);
        
        try (NetworkTransportClient transport = new NetworkTransportClient(host, port, 5000)) {
            transport.connect();
            CachingOrbitFSClient client = new CachingOrbitFSClient(transport);
            
            String rootHandle = client.open("/");
            System.out.println("SUCCESS: Connected. Listing files:");
            
            List<RPCEntry> files = client.listWithStat(rootHandle, true);
            for (RPCEntry f : files) {
                System.out.println(" - " + (f.isDir() ? "[D] " : "[F] ") + f.name() + " (" + f.size() + " bytes)");
                
                if (!f.isDir()) {
                    System.out.println(" -> Opening " + f.name() + "...");
                    String fileHandle = client.open("/" + f.name());
                    System.out.println(" -> Reading " + f.name() + "...");
                    byte[] data = client.read(fileHandle, 0, (int)Math.min(f.size(), 100));
                    System.out.println(" -> Data: " + new String(data));
                    client.close(fileHandle);
                }
            }
            
            client.close(rootHandle);
            System.out.println("ACTIONS COMPLETE.");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

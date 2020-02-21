package org.openhab.binding.devireg.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;
import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;

public class DeviSmartConfigReceiver {

    // Development only!!! Replace with localhost !
    static private final String configURL = "http://localhost:8080/rest/bindings/devireg/config";

    // This code is invoked from the CLI in a separate process, so we
    // don't make any calls to OpenHAB frameworks here. We also need
    // our own grid connection.
    public static void main(String[] args) {
        String configData = "";
        String configJSON = null;

        if (args.length < 1) {
            System.err.println("One-time password not supplied");
            System.exit(255);
        }

        try {
            URL url = new URL(configURL);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                configData += line;
            }
            reader.close();
        } catch (MalformedURLException e) {
            System.err.print(e);
            System.exit(255);
        } catch (IOException e) {
            System.err.println("Failed to connect to " + configURL);
            System.exit(10);
        }

        JSONObject json = new JSONObject(configData);
        byte[] privateKey = SDGUtils.ParseKey((String) json.get("privateKey"));

        if (privateKey == null) {
            System.err.println("Private key is not configured correctly!");
            System.exit(10);
        }

        String userName = "OpenHAB"; // TODO: Move to the config

        OSDGConnection grid = new OSDGConnection();

        grid.SetBlockingMode(true);
        grid.SetPrivateKey(privateKey);

        if (grid.ConnectToDanfoss() != OSDGResult.NO_ERROR) {
            System.err.println("Failed to connect to Danfoss grid: " + grid.getLastResultStr());
            grid.Dispose();
            System.exit(10);
        }

        System.out.println("Connected to Danfoss grid");

        OSDGConnection pairing = new OSDGConnection();
        pairing.SetBlockingMode(true);

        OSDGResult r = pairing.PairRemote(grid, args[0]);

        if (r == OSDGResult.NO_ERROR) {
            System.out.println("Pairing successful");

            byte[] phoneId = pairing.getPeerId();

            // Now we know phone's peer ID so we can establish data connection
            DeviSmartConfigConnection cfg = new DeviSmartConfigConnection();
            cfg.SetBlockingMode(true);

            if (cfg.ConnectToRemote(grid, phoneId, "dominion-config-1.0") == OSDGResult.NO_ERROR) {
                JSONObject request = new JSONObject();

                request.put("phoneName", userName);
                request.put("phonePublicKey", DatatypeConverter.printHexBinary(grid.GetMyPeerId()));
                request.put("chunkedMessage", true);

                cfg.Send(request.toString().getBytes());
                configJSON = cfg.Receive();

                if (configJSON == null) {
                    System.out.println("Failed to receive config: " + cfg.getLastResultStr());
                }

                cfg.Close();

            } else {
                System.out.println("Failed to connect to the sender: " + cfg.getLastResultStr());
            }

            cfg.Dispose();

        } else {
            System.out.println("Pairing failed: " + pairing.getLastResultStr());
        }

        pairing.Dispose();

        grid.Close();
        grid.Dispose();

        if (configJSON != null) {
            System.out.println("Received config JSON:");
            System.out.println(configJSON);

            String userdataDir = System.getProperty("openhab.userdata");

            if (userdataDir == null) {
                System.out.println("openhab.userdata is not set; configuration is not saved!");
                System.exit(10);
            }

            String configPath = userdataDir + "/devismart";

            File configDir = new File(configPath);
            if (!configDir.exists()) {
                configDir.mkdir();
            }

            String jsonPath = configDir + "/thermostats.json";
            try {
                PrintWriter configFile = new PrintWriter(jsonPath);
                configFile.print(configJSON);
                configFile.close();
            } catch (FileNotFoundException e) {
                System.err.println("Failed to write " + jsonPath + ": " + e.toString());
                System.exit(10);
            }

            System.out.println("Done");
        }
    }

}

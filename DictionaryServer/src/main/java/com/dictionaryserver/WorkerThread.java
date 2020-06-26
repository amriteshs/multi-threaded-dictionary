package com.dictionaryserver;

import java.io.*;
import java.net.*;
import org.json.*;
import java.util.*;


public class WorkerThread extends Thread {
    private final DictionaryServer ds;
    private final File file;
    private final Socket cSocket;
    private final int cCounter;

    // Constructors
    public WorkerThread(DictionaryServer ds, File file, Socket cSocket, int cCounter) {
        this.cSocket = cSocket;
        this.cCounter = cCounter;
        this.file = file;
        this.ds = ds;
    }
    
    // search meaning of word in dictionary
    private void searchMeaning(String word, JSONObject data, BufferedWriter output) throws IOException {
        if (data.has(word.toLowerCase())) {
            output.write(data.getJSONArray(word.toLowerCase()).getString(0) + "\n");
        } else {
            output.write("Word does not exist in dictionary.\n");
        }
        
        output.flush();
    }
    
    // add word to dictionary; synchronized keyword is used to maintain concurrency
    private synchronized void addWord(String word, String meaning, JSONObject data, BufferedWriter output) throws IOException {
        if (data.has(word.toLowerCase())) {
            output.write("Word already exists in dictionary.\n");
            output.flush();
        } else {
            JSONArray meaningList = new JSONArray();
            meaningList.put(meaning);
            data.put(word.toLowerCase(), meaningList);
            output.write("Dictionary updated.\n");
            output.flush();

            try (FileWriter os = new FileWriter(file, false)) {
                os.write(data.toString());
                os.flush();

                ds.serverText.append("Dictionary updated.\n");
            } catch (IOException e) {
                ds.serverText.append("Unable to write to dictionary.\n");
            }
        }
    }

    // remove word from dictionary; synchronized keyword is used to maintain concurrency
    private synchronized void removeWord(String word, JSONObject data, BufferedWriter output) throws IOException {
        if (data.has(word.toLowerCase())) {
            data.remove(word.toLowerCase());
            output.write("Dictionary updated.\n");
            output.flush();

            try (FileWriter os = new FileWriter(file, false)) {
                os.write(data.toString());
                os.flush();

                ds.serverText.append("Dictionary updated.\n");
            } catch (IOException e) {
                ds.serverText.append("Unable to write to dictionary.\n");
            }
        } else {
            output.write("Word does not exist in dictionary.\n");
            output.flush();
        }
    }
    
    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(cSocket.getInputStream(), "UTF-8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(cSocket.getOutputStream(), "UTF-8"));
            String message;
            
            while ((message = in.readLine()) != null) {
                StringTokenizer input = new StringTokenizer(message, " ");
                ds.serverText.append("Client" + cCounter + " Message: " + message + "\n");
                String operation = input.nextToken();
                String word;
                String meaning;
                
                if (operation.equals("SEARCH")) {
                    JSONObject data = new JSONObject(new JSONTokener(new FileReader(file)));
                    word = input.nextToken();
                    searchMeaning(word, data, out);
                    
                    continue;
                } else if (operation.equals("ADD")) {
                    JSONObject data = new JSONObject(new JSONTokener(new FileReader(file)));
                    word = input.nextToken();
                    meaning = input.nextToken("\n").trim();
                    addWord(word, meaning, data, out);
                    
                    continue;
                } else if (operation.equals("REMOVE")) {
                    JSONObject data = new JSONObject(new JSONTokener(new FileReader(file)));
                    word = input.nextToken();
                    removeWord(word, data, out);
                    
                    continue;
                }
                
                ds.serverText.append("Response sent.\n");
            }
        } catch(SocketException e) {
            ds.serverText.append("Socket closed.\n");
        } catch(FileNotFoundException e) {
            ds.serverText.append("Dictionary does not exist.\n");
        } catch(JSONException e) {
            ds.serverText.append("Unable to parse dictionary.\n");
        } catch(IOException e) {
            ds.serverText.append("Unable to communicate with client.\n");
        } finally {
            if (cSocket != null) {
                try {
                    cSocket.close();
                    ds.serverText.append("**************************\n");
                    ds.serverText.append("Connection terminated for Client" + cCounter + ".\n");
                } catch (IOException e) {
                    ds.serverText.append("Could not terminate client socket connection.\n");
                }
            }
        }
    }
}


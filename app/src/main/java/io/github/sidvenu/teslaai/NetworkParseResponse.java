package io.github.sidvenu.teslaai;

import android.widget.ProgressBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

final class NetworkParseResponse {
    private static URL url = null;
    private static String botResponse = null;
    private ProgressBar progressBar = null;

    // Form the url for the connection to be established
    void formURL(String talkWord, long uid) {
        try {
            talkWord = talkWord.replace(" ", "%20").replace("!", "")
                    .replace("'", "%27");
            String urlBuild = "http://api.brainshop.ai/get?bid=1054&key=" + BuildConfig.BrainShopAPIKey + "&uid="
                    + Long.toString(uid) + "&msg=" + talkWord;
            url = new URL(urlBuild);
            publishProgress(5);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    // Get the progressbar from UIActivity so as to easily update it
    void appendProgressBar(ProgressBar progress) {
        progressBar = progress;
    }

    // Establish the connection with the server and get the JSON response
    void establishConnectionGetResponse() {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            publishProgress(20);
            urlConnection.setRequestMethod("GET");
            publishProgress(35);
            publishProgress(55);
            urlConnection.connect();
            publishProgress(60);
            String jsonData = getStringFromInputStream(urlConnection.getInputStream());
            publishProgress(75);
            botResponse = parseBotResponse(jsonData);
            if (botResponse != null && botResponse.contains("<tip>"))
                botResponse = botResponse.substring(botResponse.lastIndexOf("tip>") + 4);
            publishProgress(100);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // A helper method for setting the progress value in the progressBar
    private void publishProgress(int progress) {
        progressBar.setProgress(progress);
    }

    // Get the JSON Response as a string from the server
    private String getStringFromInputStream(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        if (inputStream != null) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line);
                line = bufferedReader.readLine();
            }
        }
        return stringBuilder.toString();
    }

    // Parse the JSON response the server hath sent
    private String parseBotResponse(String jsonData) {
        try {
            JSONObject rootObject = new JSONObject(jsonData);
            return rootObject.getString("cnt").replace("\\", "");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Send back the result bot response
    String getBotResponse() {
        return botResponse;
    }

}

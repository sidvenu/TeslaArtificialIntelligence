package io.github.sidvenu.teslaai;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class UIActivity extends AppCompatActivity {

    TextToSpeech textToSpeech;
    private WebView userInputWebView;
    private WebView botResponseWebView;
    private ImageView teslaLogo;
    public ProgressBar progress;
    private final int REQUEST_CODE = 1234;
    SharedPreferences preferences;
    String chat = "Hey there!";
    // available for the device support, enabled for the user - if he/she wants it or not
    boolean isTTSAvailable = false, isTTSEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui);

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                boolean isError = false;
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        isError = true;
                    }
                } else {
                    isError = true;
                }
                isTTSAvailable = !isError;
                if (isError) {
                    Toast.makeText(UIActivity.this, "TextToSpeech is not supported", Toast.LENGTH_SHORT).show();
                    ((ImageButton) findViewById(R.id.voice_control)).setImageResource(R.drawable.tesla_voice_off);
                } else
                    ((ImageButton) findViewById(R.id.voice_control)).setImageResource(R.drawable.tesla_voice_on);
                new NetworkAsyncTask().execute("Hey there!");
            }
        });

        botResponseWebView = findViewById(R.id.botResponseWebView);
        botResponseWebView.setBackgroundColor(0);
        userInputWebView = findViewById(R.id.userInputWebView);
        userInputWebView.setBackgroundColor(0);
        teslaLogo = findViewById(R.id.teslaLogo);
        progress = findViewById(R.id.progressBar);

        /* Check if the device has a preference "uid" which each user has a unique one. If the
        device does not have it, create the preference and initialize with current milli time */
        preferences = getSharedPreferences("preference", MODE_PRIVATE);
        if (!(preferences.contains("uid"))) {
            long randomUID = System.currentTimeMillis();
            preferences.edit().putLong("uid", randomUID).apply();
        }

        /* Listen for input every time the ImageButton is clicked. If the device is not connected, then
         alert the user about it */
        ImageButton startListening = findViewById(R.id.speech_button);
        startListening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnected()) {
                    startListening();
                } else {
                    Toast.makeText(getApplicationContext(), "Please check your Internet connection", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void startListening(){
        if(textToSpeech.isSpeaking())
            textToSpeech.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, REQUEST_CODE);
    }

    // Check if the device is connected to the internet
    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = null;
        if (cm != null) net = cm.getActiveNetworkInfo();
        return net != null && net.isAvailable() && net.isConnected();
    }


    // Exit the app
    private void exit() {
        parseHTMLSetWebView(getString(R.string.exit_message), 1);
        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            chat = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            parseHTMLSetWebView(chat, 0);

            /* Check if the user gave a command for Tesla to open an app, or to exit the app, or
            just a normal chat , and do the appropriate action*/
            if (isAppOpenCommand()) {
                parseHTMLSetWebView(getString(R.string.opening_app), 1);
                new OpenApp().execute();
            } else if (isAppExitCommand()) {
                exit();
            } else new NetworkAsyncTask().execute(chat);

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Show alert dialog with apps listed in it for  user to choose
    private void showAlertDialog(final ArrayList<ApplicationInfo> appList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an application");
        builder.setItems(getAppNames(appList), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(getPackageManager().getLaunchIntentForPackage(appList.get(which).packageName));
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        if (alertDialog.getWindow() != null)
            lp.copyFrom(alertDialog.getWindow().getAttributes());
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        lp.height = (size.y) * 2 / 3;
        alertDialog.getWindow().setAttributes(lp);
    }

    // Get app names string array from a list of ApplicationInfo
    private String[] getAppNames(List<ApplicationInfo> appList) {
        ArrayList<String> appNames = new ArrayList<>();
        for (ApplicationInfo appInfo : appList) {
            appNames.add(getPackageManager().getApplicationLabel(appInfo).toString());
        }
        return appNames.toArray(new String[appNames.size()]);
    }

    // Retrieve the apps which match the given app name
    private ArrayList<ApplicationInfo> retrieveMatchedAppInfos(List<ApplicationInfo> appList, String requestedAppName) {
        ArrayList<ApplicationInfo> matchingAppNames = new ArrayList<>();
        for (ApplicationInfo appInfo : appList) {
            String appName = getPackageManager().getApplicationLabel(appInfo).toString();
            if (appName.toLowerCase().contains(requestedAppName.toLowerCase())) {
                matchingAppNames.add(appInfo);
            }
        }
        return matchingAppNames;
    }

    private boolean isAppOpenCommand() {
        return chat.toLowerCase().contains("open ");
    }

    private boolean isAppExitCommand() {
        String temp = chat.toLowerCase();
        return temp.contains("exit") || temp.contains("bye") || temp.contains("gotta go") || temp.contains("got to go");
    }

    private void speakResponse() {
        if (isTTSAvailable && isTTSEnabled) {
            HashMap<String, String> map = new HashMap<>();
            map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            textToSpeech.speak(NetworkParseResponse.botResponse, TextToSpeech.QUEUE_FLUSH, map);
        }
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    public void toggleVoiceControl(View view) {
        if (isTTSAvailable) {
            isTTSEnabled = !isTTSEnabled;
            if (isTTSEnabled) {
                ((ImageButton)findViewById(R.id.voice_control)).setImageResource(R.drawable.tesla_voice_on);
            } else {
                ((ImageButton)findViewById(R.id.voice_control)).setImageResource(R.drawable.tesla_voice_off);
                if (textToSpeech.isSpeaking())
                    textToSpeech.stop();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class NetworkAsyncTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            teslaLogo.setVisibility(View.INVISIBLE);
            progress.setProgress(0);
            progress.setVisibility(View.VISIBLE);
            botResponseWebView.setVisibility(View.INVISIBLE);
        }

        @Override
        protected String doInBackground(String... talkWord) {
            if (talkWord == null || talkWord[0].isEmpty()) return null;
            NetworkParseResponse botChat = new NetworkParseResponse();

            botChat.appendProgressBar(progress);
            botChat.formURL(talkWord[0], preferences.getLong("uid", 0));
            botChat.establishConnectionGetResponse();

            return botChat.getBotResponse();
        }

        @Override
        protected void onPostExecute(String botResponse) {
            progress.setVisibility(View.INVISIBLE);
            teslaLogo.setVisibility(View.VISIBLE);
            if (botResponse != null) {
                parseHTMLSetWebView(botResponse, 1);
            }
            botResponseWebView.setVisibility(View.VISIBLE);
            speakResponse();
        }
    }

    /* n will be 1 if we want to edit the botResponseWebView and 0 if we want to edit the
    inputWebView */
    private void parseHTMLSetWebView(String text, int n) {
        String html;
        html = "<html><head>"
                + "<style type=\"text/css\">";
        if (n == 1)
            html += "body { color: #FFFFFF; font-size:x-large; text-shadow: 2px 2px 8px #816a0e;}";
        else
            html += "body { color: #FFFFFF; font-size:x-large; text-shadow: 2px 2px 8px #2450cf31;}";
        html += "</style></head>"
                + "<body>"
                + text
                + "</body></html>";
        if (n == 1)
            botResponseWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        else
            userInputWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    // Open app based on value of chat
    @SuppressLint("StaticFieldLeak")
    private class OpenApp extends AsyncTask<Void, Void, List<ApplicationInfo>> {

        @Override
        protected List<ApplicationInfo> doInBackground(Void[] params) {
            StringBuilder requestedAppName = new StringBuilder(chat.substring(chat.indexOf("open ") + 5));

            List<ApplicationInfo> appList = getPackageManager().getInstalledApplications(0), matchingAppsList;
            matchingAppsList = retrieveMatchedAppInfos(appList, requestedAppName.toString());

        /* If the matchingAppList size be 0, then check Apps with words at the end removed too.
        For example, if "WhatsApp Pro" yields 0 results, then try searching "WhatsApp" too! */
            while (matchingAppsList.size() == 0 && requestedAppName.length() > 0) {
                if (requestedAppName.toString().endsWith(" "))
                    requestedAppName.deleteCharAt(requestedAppName.lastIndexOf(" "));
                int index = requestedAppName.length() - 1;

                for (Character curChar = requestedAppName.charAt(index);
                     !curChar.equals(' ') && !curChar.toString().isEmpty() && index >= 0; ) {
                    curChar = requestedAppName.charAt(index);
                    requestedAppName.deleteCharAt(index);
                    index--;
                }
                //The above for loop removes the word at the end of the requested app name
                matchingAppsList = retrieveMatchedAppInfos(appList, requestedAppName.toString());
            }
            return matchingAppsList;
        }

        @Override
        protected void onPostExecute(List<ApplicationInfo> matchingAppsList) {
            if (matchingAppsList.size() == 0)
                parseHTMLSetWebView(getString(R.string.app_not_found), 1);
            else if (matchingAppsList.size() == 1) {
                parseHTMLSetWebView(getString(R.string.app_opened), 1);
                startActivity(getPackageManager().getLaunchIntentForPackage(matchingAppsList.get(0).packageName));
            } else {
                parseHTMLSetWebView(getString(R.string.app_opened), 1);
                showAlertDialog((ArrayList<ApplicationInfo>) matchingAppsList);
            }
        }
    }


}


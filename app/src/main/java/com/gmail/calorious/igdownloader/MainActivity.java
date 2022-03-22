package com.gmail.calorious.igdownloader;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private final int STORAGE_REQUEST_CODE = 320550;
    private EditText link_textbox;
    private Button paste_link_button, download_button, severe_error_action_button;
    private WebView authorization_window;
    private TextView error_message;
    private TextView severe_error_message;
    private ScheduledExecutorService executor;

    // INSTAGRAM AUTHORIZATION TOKEN SHOULD ATTEMPT TO BE SAVED INTO THE DEVICE'S INTERNAL STORAGE AS APP-SPECIFIC DATA
    // Technically, you don't need Instagram API to access posts from public accounts as you can use the flag '?__a=1' to view raw json data behind the post.
    // Hmm, seems like you may need the Instagram API to view private posts as authorization is required, unless.. you are able to include authorization in the request to the link with ?__a=1.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadScheduledExecutor();
        setContentView(R.layout.main_activity);
        View main_activity_view = findViewById(R.id.main_layout); // Refers to the RelativeLayout object
        requestPermissions("STORAGE");
        // Initialize components
        paste_link_button = findViewById(R.id.paste_link_button);
        download_button = findViewById(R.id.download_button);
        link_textbox = findViewById(R.id.instagram_link_input);
        authorization_window = findViewById(R.id.authorization_window);
        error_message = findViewById(R.id.error_message);
        severe_error_message = findViewById(R.id.error_occurred_screen_message);
        severe_error_action_button = findViewById(R.id.error_action_button);
        File charMapFile = new File(getFilesDir(), "internal_charmap.json");
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (!(charMapFile.exists())) {
                try {
                    // Note that this requires the storage permission first.
                    charMapFile.createNewFile();
                } catch (IOException ex) {
                    Log.e("MainActivity", "Unable to create the charmap file.");
                    setContentView(R.layout.error_occurred);
                    severe_error_message.setText(R.string.severe_error_charmap_creation);
                    severe_error_action_button.setText(R.string.action_button_text_close_application);
                    severe_error_action_button.setOnClickListener((view) -> {
                        finish();
                        System.exit(0);
                    });
                }
            }
        }
        PopupWindow popup = new PopupWindow(authorization_window);
        popup.showAtLocation(main_activity_view, Gravity.CENTER, 0, 0);
        popup.setElevation(30);
    }


    // Permissions
    private void requestPermissions(String permission_or_permission_group) {
        if (permission_or_permission_group.equalsIgnoreCase("storage")) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                return;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST_CODE) {
            if (Arrays.stream(grantResults).allMatch(i -> i == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Successfully granted permission to STORAGE!", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Error: Could not obtain STORAGE permission.", Toast.LENGTH_LONG).show();
        }
    }


    // Button actions
    public void pasteClipboardData(View v) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData data = clipboardManager.getPrimaryClip();
        ClipData.Item item = data.getItemAt(0);
        CharSequence text = item.getText();
        link_textbox.setText(text);
        paste_link_button.setText(R.string.paste_data_temp_msg);
        paste_link_button.setEnabled(false);
        executor.schedule(() -> {
           paste_link_button.setText(R.string.paste_link_button);
           paste_link_button.setEnabled(true);
        }, 2, TimeUnit.SECONDS);
    }

    public void download(View v) {
        // TODO Download method
        String text = link_textbox.getText().toString();
        download_button.setText(R.string.download_button_pending);
        if(isInstagramLink(text)) {
            // Continue download
            try {
                int result = verifyLink(text);
                if(result == 401) {
                    highlightError("You need to login to Instagram to be able to download this content. [INACCESSIBLE_ACCOUNT_PRIVATE]");
                    download_button.setText(R.string.download_button);
                    return;
                }
                if(result == 404) {
                    highlightError("Instagram was unable to find this content. [INACCESSIBLE_NOT_FOUND]");
                    download_button.setText(R.string.download_button);
                    return;
                }
            } catch (IOException e) {
               Log.e("Instagram Link Handler", "Unable to verify link as the method ran into an exception.");
               e.printStackTrace();
            }

            return;
        }
        highlightError("This does not seem like a valid Instagram link. [INACCESSIBLE_MALFORMED_URL]");
    }

    public void authorize(View v) {
        // TODO Authorize method (with WebView), if token is still valid then disable this button and set WebView visibility to gone.
    }


    // Misc.

    /* Verifies if an instagram post link directs to an actual workable post
     * 0 - No issues
     * 404 - Not found
     * 401 - Unauthorized (Requires authorization token)
     * -2 - Severe Error (Instagram may be down)
     * -3 - JSON Parse Error
     * -185 - Other HTTP Error (lazy to handle)
     */
    private int verifyLink(String link) throws IOException {
        StringBuilder stringBuilder = new StringBuilder(link);
        if(link.endsWith("/")) {
            stringBuilder.append("?__a=1");
        } else {
            stringBuilder.append("/?__a=1");
        }
        String newLink = stringBuilder.toString();
        URL url1 = new URL(newLink);
        HttpsURLConnection connection1 = (HttpsURLConnection) url1.openConnection();
        connection1.setRequestMethod("GET");
        connection1.connect();
        int response1 = connection1.getResponseCode();
        if(response1 != 200) {
            return -2; // how did a ?__a=1 page return a non 200 code? It's bare minimum should atleast be an empty JSON '{}'
        }
        String line;
        StringBuilder response1Content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection1.getInputStream()));
        while ((line = reader.readLine()) != null) {
            response1Content.append(line);
        }
        JSONObject jsonObj;
        try {
            jsonObj = new JSONObject(response1Content.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("JSON Parser", "Failed to parse: " + response1Content + "into a JSONObject.");
            return -3;
        }
        if(jsonObj.length() == 0) {
            // No data - JSON string is {}
            Log.d("JSON Parser", "JSON String: " + jsonObj);
            URL url2 = new URL(link); // Try the base link next and see if it returns 404 or if its a redirection link (302) [REDIRECTION MEANS UNAUTHORIZED]
            HttpsURLConnection connection2 = (HttpsURLConnection) url2.openConnection();
            connection2.setInstanceFollowRedirects(false);
            // TODO ADD AUTHORIZATION TOKENS IF APPLICABLE
            connection2.connect();
            // 302 refers to a temporary redirect, which is triggered by its Unauthorized state. The redirection will cause the User to redirect to the account page.
            if(connection2.getResponseCode() == 302) {
                // Unauthorized
                return 401;
            }
            if(connection2.getResponseCode() == 404) {
                return 404;
            }
            if(connection2.getResponseCode() == 200) {
                Log.e("Instagram Link Handler", "" + link + " returned a status code 200 but it had no JSON data.");
                return 0; // So its a public post but theres no JSON data??
            }
            return -185;
        }
        // Return no error since the JSON data is present.
        return 0;
    }

    private boolean isInstagramLink(String link) {
        if(link.startsWith("https://www.instagram.com")) return true;
        if(link.startsWith("https://instagram.com")) return true;
        if(link.startsWith("instagram.com")) return true;
        return false;
    }
    private void highlightError(String errorMessage) {
        error_message.setText(errorMessage);
        link_textbox.setTextColor(Color.RED);
        link_textbox.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.edit_text_highlight));
        link_textbox.setOnClickListener((view) -> {
            link_textbox.setTextColor(Color.BLACK);
            error_message.setText(" ");
            error_message.setVisibility(View.INVISIBLE);
            link_textbox.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.edit_text_non_highlight));
        });
    }

    // This method will not check instagram link for its validity apart from whether it requires an authorization token.
    private List<String> obtainAllContentURLs(String instagramLink, String authorizationToken) throws IOException, JSONException {
        if (verifyLink(instagramLink) == 401) {
            // PRIVATE POST
            // Use authorization token
            // Obtain media_id from converting the Base64 to Base10
        }
        if ("".equals(authorizationToken) || authorizationToken.isEmpty())
            Log.e("Instagram API Handler", "Ignoring the presence of Authorization Token: verifyLink() did not return ERR_UNAUTHORIZED.");
        // PUBLIC POST
        StringBuilder builder = new StringBuilder(instagramLink);
        if (instagramLink.endsWith("/")) {
            builder.append("?__a=1");
        } else {
            builder.append("/?__a=1");
        }
        InputStream inputStream = new URL(builder.toString()).openStream();
        final String[] jsonString = new String[1];
        executor.execute(() -> jsonString[0] = obtainJSONString(inputStream));
        try {
            Thread.sleep(1000);
        } catch(InterruptedException ignored) {}
        JSONObject jsonObject = new JSONObject(jsonString[0]);
        // Determine the presence of a parent JSONArray key 'carousel_media' which indicates whether the post has multiple media objects.
        JSONArray carousel_media;
        try {
            carousel_media = jsonObject.getJSONArray("carousel_media");
        } catch(JSONException ex) {
            Log.d("Instagram Content Handler", "Could not find 'carousel_media' key in Instagram dump... Retrieving a single instagram photo.");
            // Single media
            // Obtain original_width and original_height from the parent node (jsonObject)
            int original_width = jsonObject.getInt("original_width");
            int original_height = jsonObject.getInt("original_height");
            // Obtain image_versions2 as a JSONObject, then retrieve the candidates key as a JSONArray
            JSONArray candidates = jsonObject.getJSONObject("image_versions2").getJSONArray("candidates");
            // Match the width and height of each object in the array and find the ORIGINAL height and width, together with its corresponding url.
            // Refer to https://stackoverflow.com/questions/1568762/accessing-members-of-items-in-a-jsonarray-with-java
            for(int i = 0; i < candidates.length(); ++i) { // TODO If it doesnt work, suggest changing ++i to i++
                JSONObject record = candidates.getJSONObject(i);
                int width = record.getInt("width");
                int height = record.getInt("height");
                Log.d("Instagram Content Handler", "Found candidate with width: " + width + ", height: " + height + ", and url: " + record.getString("url") + ".");
                if(width == original_width || height == original_height) { // One matching resolution SHOULD be enough.
                    Log.d("Instagram Content Handler", "Successfully found a width or height that matches the original.");
                    String url = record.getString("url");
                    return Collections.singletonList(url);
                }
            }
            Log.e("Instagram Content Handler", "End of candidates key and a matching URL with an original width or height was not found.");
            Log.e("CONTENT DUMP", candidates.toString());
            return null;
        }
        // Multiple media

        return;
    }

    private String obtainJSONString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder jsonURL_builder = new StringBuilder();
        int cp;
        try {
            while ((cp = reader.read()) != -1) {
                jsonURL_builder.append((char) cp);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            Log.e("Instagram API Handler", "Unable to read contents of JSON.");
            return null;
        }
        return jsonURL_builder.toString();
    }

    private String convertIntegerBase(String str, int frmBase, int toBase) {
        return Integer.toString(Integer.parseInt(str, frmBase), toBase);
    }
}

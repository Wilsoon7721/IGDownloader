package com.gmail.calorious.igdownloader;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private final int STORAGE_REQUEST_CODE = 320550;
    private Button paste_link_button, download_button;
    private EditText link_textbox;
    private WebView authorization_window;
    // INSTAGRAM AUTHORIZATION TOKEN SHOULD ATTEMPT TO BE SAVED INTO THE DEVICE'S INTERNAL STORAGE AS APP-SPECIFIC DATA
    // Technically, you don't need Instagram API to access posts from public accounts as you can use the flag '?__a=1' to view raw json data behind the post.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        View main_activity_view = findViewById(R.id.main_layout);
        requestPermissions("STORAGE");
        // Initialize components
        paste_link_button = findViewById(R.id.paste_link_button);
        download_button = findViewById(R.id.download_button);
        link_textbox = findViewById(R.id.instagram_link_input);
        authorization_window = findViewById(R.id.authorization_window);
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
        if(requestCode == STORAGE_REQUEST_CODE) {
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
    }

    public void download(View v) {
        // TODO Download method
    }

    public void authorize(View v) {
        // TODO Authorize method (with WebView), if token is still valid then disable this button and set WebView visibility to gone.
    }

}

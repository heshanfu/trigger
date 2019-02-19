package com.example.trigger.ssh;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

import com.example.trigger.R;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import static com.example.trigger.Utils.*;
import static com.example.trigger.ssh.SshTools.keypairToBytes;


public class KeyPairActivity extends AppCompatActivity {
    // hack
    private KeyPairPreference preference;
    private AlertDialog.Builder builder;
    private Button createButton;
    private Button importButton;
    private Button exportButton;
    private Button cancelButton;
    private Button okButton;
    private TextView keyInfo;
    private TextView publicKey;
    private TextView pathSelection;
    private KeyPair keypair;
    private Uri path_uri;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keypair);

        // hack!
        this.preference = KeyPairPreference.self;
        this.keypair = this.preference.getKeyPair();

        builder = new AlertDialog.Builder(this);
        createButton = (Button) findViewById(R.id.CreateButton);
        importButton = (Button) findViewById(R.id.ImportButton);
        exportButton = (Button) findViewById(R.id.ExportButton);
        okButton = (Button) findViewById(R.id.OkButton);
        cancelButton = (Button) findViewById(R.id.CancelButton);
        keyInfo = (TextView) findViewById(R.id.KeyInfo);
        publicKey = (TextView) findViewById(R.id.PublicKey);
        pathSelection = (TextView) findViewById(R.id.PathSelection);

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewSshIdentity();
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (KeyPairActivity.this.keypair == null) {
                    showErrorMessage("No Key", "No key loaded to export.");
                    return;
                }

                if (path_uri == null) {
                    showErrorMessage("Missing Target", "No output folder was selected.");
                    return;
                }

                try {
                    SshTools.writeKeyFiles(path_uri.getPath(), KeyPairActivity.this.keypair);
                    showErrorMessage("Success", "Wrote id_rsa/id_rsa.pub into.");
                } catch (Exception e) {
                    showErrorMessage("Error", e.toString());
                }
            }
        });

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (path_uri == null) {
                    showErrorMessage("Missing Target", "No public key file was selected.");
                    return;
                }

                startFileSelectActivity();
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                if (KeyPairActivity.this.keypair != null) {
                    preference.setKeyPair(KeyPairActivity.this.keypair);
                }
                KeyPairActivity.this.finish();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // persist your value here
                KeyPairActivity.this.finish();
            }
        });

        updateKeyInfo();
    }

    void updateKeyInfo() {
        if (this.keypair == null) {
            this.keyInfo.setText("<no key loaded>");
            this.publicKey.setText("<no key loaded>");
        } else {
            SshTools.KeyPairData data = keypairToBytes(this.keypair);

            this.keyInfo.setText(this.keypair.getFingerPrint());
            this.publicKey.setText(new String(data.pubkey));
        }
    }

    void createNewSshIdentity() {
        try {
            JSch jsch = new JSch();
            keypair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        } catch (Exception e) {
            Log.e("KeyPairActivity", "createNewSshIdentity: error in SshRequestHandler.generateNewIdentity: " + e.toString());
        }

        updateKeyInfo();
    }

    private static final int SELECT_TOKEN_FILE_ACTIVITY_REQUEST = 0x1;
    private static final int SCAN_TOKEN_ACTIVITY_REQUEST = 0x2;

    private static final int READ_EXTERNAL_STORAGE_PERMISSION_REQUEST = 0x1;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST = 0x2;
    private static final int CAMERA_PERMISSION_REQUEST = 0x3;

    private void startFileSelectActivity() {
        Intent documentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        documentIntent.setType("*/*");
        documentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(documentIntent, SELECT_TOKEN_FILE_ACTIVITY_REQUEST);
    }

    // called from onActivityResult
    private void importKeyFile() {
        if (path_uri == null) {
            Log.d("KeyPairActivity", "importKeyFile: path_uri is null");
            return;
        } else {
            Log.d("KeyPairActivity", "importKeyFile: " + path_uri.toString());
        }

        byte[] array = readAllBytes(new File(getFilesDir(), "id_rsa.pub"));
        if (array == null) {
            Log.e("KeyPairActivity", "array is null");
        } else {
            Log.d("KeyPairActivity", "file content: " + new String(array));
        }

        try {
            JSch jsch = new JSch();
            this.keypair = KeyPair.load(jsch, path_uri.toString());
        } catch (Exception e) {
            showErrorMessage("Error", "Error occured while processing key file: " + e.toString());
        }

        updateKeyInfo();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Activity.RESULT_CANCELED == resultCode) {
            return;
        }

        switch (requestCode) {
            case SELECT_TOKEN_FILE_ACTIVITY_REQUEST:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE_PERMISSION_REQUEST);
                } else {
                    // TODO: cannot access files yet - permissions..
                    path_uri = data.getData();
                    if (path_uri == null) {
                        pathSelection.setText("none");
                    } else {
                        pathSelection.setText(path_uri.toString());

                        // test to see if we can access the file
                        if (!(new File(path_uri.toString())).exists()) {
                            showErrorMessage("File Not Found", "Private key file not found: " + path_uri.toString());
                            break;
                        }
                    }
                    importKeyFile();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST:
            case READ_EXTERNAL_STORAGE_PERMISSION_REQUEST:
                if(grantResults.length > 0
                        && PackageManager.PERMISSION_GRANTED == grantResults[0]
                        && PackageManager.PERMISSION_GRANTED == grantResults[1]) {
                    // permissions granted
                    importKeyFile();
                } else {
                    showErrorMessage("Read Permissions Required", "External storage read/write permissions are required for this action.");
                }
                break;
            case CAMERA_PERMISSION_REQUEST:
                if(grantResults.length > 0 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                    // permissions granted
                } else {
                    showErrorMessage("Camera Permissions Required", "Camera access permissions for external storage are required for this action.");
                }
                break;
        }
    }
}

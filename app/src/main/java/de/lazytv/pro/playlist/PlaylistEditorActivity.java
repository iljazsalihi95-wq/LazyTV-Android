package de.lazytv.pro.playlist;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.UUID;

import de.lazytv.pro.R;
import de.lazytv.pro.activation.ActivationGuard;
import de.lazytv.pro.live.LiveTvActivity;

public class PlaylistEditorActivity extends Activity {
    public static final String EXTRA_ID = "playlist_id";
    private static final int PICK_M3U = 7001;

    private PlaylistStorage storage;
    private Playlist current;
    private Spinner type;
    private EditText name;
    private EditText url;
    private EditText user;
    private EditText pass;
    private EditText mac;
    private View urlRow;
    private View fileRow;
    private View xtreamFields;
    private View stalkerFields;
    private TextView fileName;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ActivationGuard.enforce(this)) return;
        setContentView(R.layout.activity_playlist_editor);
        storage = new PlaylistStorage(this);
        bind();
        setupType();

        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id != null) {
            current = storage.get(id);
            if (current != null) fill(current);
        } else {
            String preset = getIntent().getStringExtra("preset_type");
            if ("M3U".equals(preset)) type.setSelection(1);
            else if ("XTREAM".equals(preset)) type.setSelection(2);
            else if ("STALKER".equals(preset)) type.setSelection(3);
        }

        findViewById(R.id.pick_file).setOnClickListener(view -> pickFile());
        findViewById(R.id.save_playlist).setOnClickListener(view -> save());
    }

    private void bind() {
        type = findViewById(R.id.type_spinner);
        name = findViewById(R.id.input_name);
        url = findViewById(R.id.input_url);
        user = findViewById(R.id.input_username);
        pass = findViewById(R.id.input_password);
        mac = findViewById(R.id.input_mac);
        urlRow = findViewById(R.id.url_row);
        fileRow = findViewById(R.id.file_row);
        xtreamFields = findViewById(R.id.xtream_fields);
        stalkerFields = findViewById(R.id.stalker_fields);
        fileName = findViewById(R.id.file_name);
    }

    private void setupType() {
        String[] labels = {"M3U URL", "Zgjidh skedar M3U nga pajisja", "Xtream Codes", "Stalker / Portal"};
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderType();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private PlaylistType selectedType() {
        switch (type.getSelectedItemPosition()) {
            case 1:
                return PlaylistType.M3U_FILE;
            case 2:
                return PlaylistType.XTREAM_CODES;
            case 3:
                return PlaylistType.STALKER_PORTAL;
            default:
                return PlaylistType.M3U_URL;
        }
    }

    private void renderType() {
        PlaylistType selected = selectedType();
        fileRow.setVisibility(selected == PlaylistType.M3U_FILE ? View.VISIBLE : View.GONE);
        urlRow.setVisibility(selected == PlaylistType.M3U_FILE ? View.GONE : View.VISIBLE);
        xtreamFields.setVisibility(selected == PlaylistType.XTREAM_CODES ? View.VISIBLE : View.GONE);
        stalkerFields.setVisibility(selected == PlaylistType.STALKER_PORTAL ? View.VISIBLE : View.GONE);
    }

    private void fill(Playlist playlist) {
        name.setText(playlist.getName());
        url.setText(playlist.getUrl());
        user.setText(playlist.getUsername());
        pass.setText(playlist.getPassword());
        mac.setText(playlist.getMacAddress());
        int position = playlist.getType() == PlaylistType.M3U_FILE ? 1
                : playlist.getType() == PlaylistType.XTREAM_CODES ? 2
                : playlist.getType() == PlaylistType.STALKER_PORTAL ? 3 : 0;
        type.setSelection(position);
        if (playlist.getType() == PlaylistType.M3U_FILE) {
            fileName.setText(displayName(Uri.parse(playlist.getUrl())));
        }
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "audio/x-mpegurl",
                "application/vnd.apple.mpegurl",
                "text/plain",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_M3U);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_M3U || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri selectedFile = data.getData();
        try {
            int offeredFlags = data.getFlags();
            if ((offeredFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(selectedFile, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            try (InputStream stream = getContentResolver().openInputStream(selectedFile)) {
                if (stream == null) throw new IllegalStateException("File cannot be opened");
            }
        } catch (Exception error) {
            Toast.makeText(this, "Skedari nuk mund të lexohet. Zgjidhe përsëri.", Toast.LENGTH_LONG).show();
            return;
        }

        url.setText(selectedFile.toString());
        String selectedName = displayName(selectedFile);
        fileName.setText(selectedName);
        if (name.getText().toString().trim().isEmpty()) {
            name.setText(stripM3uExtension(selectedName));
        }
    }

    private String displayName(Uri uri) {
        String displayName = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) displayName = cursor.getString(column);
            }
        } catch (Exception ignored) {
        }
        return displayName == null ? "M3U file" : displayName;
    }

    private String stripM3uExtension(String value) {
        return value.replaceFirst("(?i)\\.(m3u8?|txt)$", "");
    }

    private void save() {
        PlaylistType selected = selectedType();
        String id = current == null ? UUID.randomUUID().toString() : current.getId();
        String endpoint = url.getText().toString().trim().replace("&amp;", "&");
        Playlist playlist = new Playlist(
                id,
                name.getText().toString().trim(),
                selected,
                endpoint,
                user.getText().toString().trim(),
                pass.getText().toString(),
                mac.getText().toString().trim(),
                System.currentTimeMillis()
        );

        String error = PlaylistValidator.validateForConnect(this, playlist);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }

        storage.save(playlist);
        storage.setActive(playlist.getId());
        if (selected == PlaylistType.M3U_FILE) {
            Toast.makeText(this, "Skedari u ruajt. Po hap Live TV…", Toast.LENGTH_SHORT).show();
            Intent live = new Intent(this, LiveTvActivity.class);
            live.putExtra("playlist_id", playlist.getId());
            live.putExtra("source_scope", "PROVIDER");
            live.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(live);
        } else {
            Toast.makeText(this, "U ruajt. Katalogu ngarkohet kur hap rubrikën.", Toast.LENGTH_SHORT).show();
            Intent home = new Intent(this, de.lazytv.pro.MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
        }
        finish();
    }
}

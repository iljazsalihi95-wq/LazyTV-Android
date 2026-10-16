package de.lazytv.pro.cms;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Compatibility entry point for CMS links.
 *
 * LazyTV PRO is a native application and must not host provider/content pages in
 * a WebView. Existing callers can keep targeting this Activity while URLs are
 * handed to an external browser/app through ACTION_VIEW.
 */
public class EmbeddedWebActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String url = getIntent().getStringExtra("url");
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Linku nuk është i disponueshëm.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            Uri uri = Uri.parse(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                Toast.makeText(this, "Link i pavlefshëm.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Intent external = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(external);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nuk u gjet aplikacion për hapjen e linkut.", Toast.LENGTH_SHORT).show();
        } finally {
            finish();
        }
    }
}

package de.lazytv.pro.catalog;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Loads a LazyMapper server tab directly from its Google Sheet CSV export. */
public final class SheetServerCatalogSource {
    public Catalog load(Context context, String spreadsheetId, String sheetName, String serverId) throws CatalogException {
        Catalog out = new Catalog();
        LinkedHashMap<String, Category> cats = new LinkedHashMap<>();
        HttpURLConnection connection = null;
        try {
            String endpoint = "https://docs.google.com/spreadsheets/d/" + spreadsheetId +
                    "/gviz/tq?tqx=out:csv&sheet=" + URLEncoder.encode(sheetName, "UTF-8");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LazyTV-PRO/1.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new CatalogException("Serveri Premium nuk është i disponueshëm për momentin.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String headerLine = reader.readLine();
            if (headerLine == null) throw new CatalogException("Serveri Premium nuk ka të dhëna.");
            List<String> headers = csv(headerLine);
            int idCol = column(headers, "ID"), countryCol = column(headers, "COUNTRY"), countryCodeCol = column(headers, "COUNTRY_CODE"), categoryCol = column(headers, "CATEGORY"), nameCol = column(headers, "NAME"), urlCol = column(headers, "STREAM_URL_CMD"), logoCol = column(headers, "LOGO"), statusCol = column(headers, "STATUS"), serverCol = column(headers, "SERVER_ID");
            if (nameCol < 0 || urlCol < 0) throw new CatalogException("Formati i Serverit Premium nuk është i vlefshëm.");
            String line; int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                List<String> values = csv(line);
                String rowServer = value(values, serverCol);
                if (serverCol >= 0 && !rowServer.isEmpty() && !serverId.equalsIgnoreCase(rowServer)) continue;
                String status = value(values, statusCol);
                if (!status.isEmpty() && ("DISABLED".equalsIgnoreCase(status) || "OFF".equalsIgnoreCase(status))) continue;
                String streamUrl = value(values, urlCol).trim();
                String name = value(values, nameCol).trim();
                if (streamUrl.isEmpty() || name.isEmpty()) continue;
                String countryCode = value(values, countryCodeCol).trim().toUpperCase(Locale.US);
                String country = value(values, countryCol).trim();
                if (countryCode.isEmpty()) countryCode = country.isEmpty() ? "XX" : country;
                String categoryName = value(values, categoryCol).trim();
                if (categoryName.isEmpty()) categoryName = "Tjera";
                String categoryId = serverId + "|" + countryCode + "|" + categoryName;
                if (!cats.containsKey(categoryId)) {
                    Category category = new Category(categoryId, categoryName, CatalogType.LIVE, countryCode);
                    cats.put(categoryId, category); out.categories.add(category);
                }
                String itemId = value(values, idCol).trim(); if (itemId.isEmpty()) itemId = serverId + "_" + row;
                out.items.add(new StreamItem(itemId, name, categoryId, value(values, logoCol).trim(), streamUrl, "", CatalogType.LIVE, new ArrayList<String>()));
            }
            if (out.items.isEmpty()) throw new CatalogException("Serveri Premium nuk ka kanale aktive.");
            return out;
        } catch (CatalogException e) { throw e; }
        catch (Exception e) { throw new CatalogException("Serveri Premium nuk mund të ngarkohet për momentin."); }
        finally { if (connection != null) connection.disconnect(); }
    }
    private static int column(List<String> h, String name) { for (int i=0;i<h.size();i++) if (name.equalsIgnoreCase(h.get(i).trim())) return i; return -1; }
    private static String value(List<String> row, int i) { return i>=0 && i<row.size() ? row.get(i) : ""; }
    private static List<String> csv(String line) {
        ArrayList<String> out=new ArrayList<>(); StringBuilder b=new StringBuilder(); boolean q=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){out.add(b.toString());b.setLength(0);}else b.append(c);}out.add(b.toString());return out;
    }
}

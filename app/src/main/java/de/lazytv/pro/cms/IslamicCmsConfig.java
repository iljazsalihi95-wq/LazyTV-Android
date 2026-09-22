package de.lazytv.pro.cms;
public final class IslamicCmsConfig {
 private IslamicCmsConfig(){}
 public static final String SPREADSHEET_ID="1COgz8NtU9QzKg_4BgNvykKqeHepk3oIGO-DsWXQhRsQ",FILMS="VOD_ISLAM_FILM",SERIES="VOD_ISLAM_SERIES",LECTURES="VOD_ISLAM_LECTURES",DOCUMENTARIES="VOD_ISLAM_DOCS",KIDS="VOD_ISLAM_KIDS";
 public static String csv(String sheet){return "https://docs.google.com/spreadsheets/d/"+SPREADSHEET_ID+"/gviz/tq?tqx=out:csv&sheet="+java.net.URLEncoder.encode(sheet);}
}
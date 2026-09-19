package de.lazytv.pro.catalog;
import java.util.*;
public final class CountryGroup {
 public final String key,label; public final List<Category> categories=new ArrayList<>();
 public CountryGroup(String key,String label){this.key=key;this.label=label;}
}
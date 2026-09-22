package de.lazytv.pro.catalog;
import android.content.Context;
/**
 * Dedicated LazyTV Trial source.
 * This source is intentionally separate from user M3U/Xtream/Stalker providers.
 * Provider credentials and private trial stream tokens must never be committed here.
 */
public final class TrialCatalogSource {
 public Catalog load(Context context)throws CatalogException{
  return new BuiltInCatalogSource().load(context);
 }
}

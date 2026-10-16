package de.lazytv.pro.stb;
import org.junit.Test;import static org.junit.Assert.*;
public class StbFavoritesTest {
 @Test public void profileAndPortalIdentityStaySeparate(){assertNotEquals(StbFavorites.identity("one","https://example.test/a/c/"),StbFavorites.identity("two","https://example.test/a/c/"));assertNotEquals(StbFavorites.identity("one","https://example.test/a/c/"),StbFavorites.identity("one","https://example.test/b/c/"));assertEquals(StbFavorites.identity("one","https://example.test/a/c/"),StbFavorites.identity("one","https://example.test/a/portal.php"));}
}

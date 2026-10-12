package de.lazytv.pro.catalog;

/** Status-only diagnostic: provider response bodies may contain credentials. */
public final class ProviderHttpException extends CatalogException {
    public final int status;
    public ProviderHttpException(int status) {
        super(message(status));this.status=status;
    }
    private static String message(int status) {
        switch(status){
            case 402:return "HTTP 402: provideri refuzoi kërkesën; kontrollo autorizimin dhe gjendjen e llogarisë.";
            case 401:return "HTTP 401: autorizimi i providerit u refuzua.";
            case 403:return "HTTP 403: provideri refuzoi headers, session-in ose të drejtën e hyrjes.";
            case 409:return "HTTP 409: konflikt i session-it/kërkesës pas retry të kufizuar.";
            default:return "Serveri ktheu HTTP "+status;
        }
    }
}

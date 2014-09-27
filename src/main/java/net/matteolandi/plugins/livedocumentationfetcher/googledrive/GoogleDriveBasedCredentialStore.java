package net.matteolandi.plugins.livedocumentationfetcher.googledrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.jackson.map.ObjectMapper;
import rx.Observable;
import rx.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class GoogleDriveBasedCredentialStore implements CredentialStore {
    private static final String MIME_TYPE = "application/json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Log log;
    private final GoogleDriveUtils googleDriveUtils;
    private final Drive service;

    private GoogleDriveBasedCredentialStore(final Log log, final HttpTransport httpTransport,
                                            final JsonFactory jsonFactory, final GoogleDriveUtils googleDriveUtils,
                                            final String serviceAccountEmail,
                                            final java.io.File serviceAccountPrivateKeyPath)
            throws GeneralSecurityException, IOException {
        this.log = log;
        this.googleDriveUtils = googleDriveUtils;

        final GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(serviceAccountEmail)
                .setServiceAccountScopes(Arrays.asList(DriveScopes.DRIVE))
                .setServiceAccountPrivateKeyFromP12File(serviceAccountPrivateKeyPath)
                .build();
        this.service = new Drive.Builder(httpTransport, jsonFactory, null)
                .setApplicationName("DriveBasedCredentialStore")
                .setHttpRequestInitializer(credential).build();
    }


    public static Observable<GoogleDriveBasedCredentialStore>
    observe(final Log log, final HttpTransport httpTransport, final JsonFactory jsonFactory,
            final GoogleDriveUtils googleDriveUtils, final String serviceAccountEmail,
            final java.io.File serviceAccountPrivateKeyPath) {
        return Observable.create(new Observable.OnSubscribe<GoogleDriveBasedCredentialStore>() {
            @Override
            public void call(Subscriber<? super GoogleDriveBasedCredentialStore> s) {
                try {
                    s.onNext(new GoogleDriveBasedCredentialStore(log, httpTransport, jsonFactory, googleDriveUtils,
                            serviceAccountEmail, serviceAccountPrivateKeyPath));
                    s.onCompleted();
                } catch (GeneralSecurityException e) {
                    log.warn(String.format("Cannot create Google Drive credential store: %s", e.getMessage()), e);
                    s.onError(e);
                } catch (IOException e) {
                    log.warn(String.format("Cannot create Google Drive credential store: %s", e.getMessage()), e);
                    s.onError(e);
                }
            }
        });
    }

    @Override
    public boolean load(final String userId, final Credential credential) throws IOException {
        final File file = googleDriveUtils.retrieveFileByTitle(service, userId);
        if (file == null) {
            log.debug(String.format("Cannot load credentials for user '%s'", userId));
            return false;
        }

        final InputStream inputStream = googleDriveUtils.fetchByTitle(service, userId);

        final StoredCredential storedCredential
                = objectMapper.readValue(inputStream, StoredCredential.class);
        credential.setAccessToken(storedCredential.accessToken);
        credential.setRefreshToken(storedCredential.refreshToken);
        credential.setExpirationTimeMilliseconds(storedCredential.expirationTimeMilliseconds);

        log.debug(String.format("Loaded credentials for user '%s': %s", userId, storedCredential));
        return true;
    }

    @Override
    public void store(final String userId, final Credential credential) throws IOException {
        final StoredCredential storedCredential = new StoredCredential();
        storedCredential.accessToken = credential.getAccessToken();
        storedCredential.refreshToken = credential.getRefreshToken();
        storedCredential.expirationTimeMilliseconds = credential.getExpirationTimeMilliseconds();

        googleDriveUtils.uploadOrUpdateByTitle(
                service, userId, MIME_TYPE, objectMapper.writeValueAsString(storedCredential));
        log.debug(String.format("Stored credentials for user '%s': %s", userId, storedCredential));
    }

    @Override
    public void delete(final String userId, final Credential credential) throws IOException {
        googleDriveUtils.deleteFileByTitle(service, userId);
        log.debug(String.format("Deleted credentials for user '%s'", userId));
    }

    public static class StoredCredential {
        public String accessToken;
        public String refreshToken;
        public long expirationTimeMilliseconds;

        @Override
        public String toString() {
            return "StoredCredential{" +
                    "accessToken='" + accessToken + '\'' +
                    ", refreshToken='" + refreshToken + '\'' +
                    ", expirationTimeMilliseconds=" + expirationTimeMilliseconds +
                    '}';
        }
    }
}

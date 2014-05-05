package net.matteolandi.plugins.livedocumentationfetcher.googledrive;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

public class GoogleDriveService {

    private static final String CLIENT_ID = "service";
    private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

    private final CredentialStore credentialStore;
    private final GoogleDriveUtils googleDriveUtils;
    private final Drive service;

    public GoogleDriveService(final HttpTransport httpTransport, final JsonFactory jsonFactory,
                              final CredentialStore credentialStore, final GoogleDriveUtils googleDriveUtils,
                              final String clientId, final String clientSecret, final String authCode)
            throws Exception {
        this.credentialStore = credentialStore;
        this.googleDriveUtils = googleDriveUtils;
        this.service = getService(httpTransport, jsonFactory, credentialStore, clientId, clientSecret, authCode);
    }

    private Drive getService(final HttpTransport httpTransport, final JsonFactory jsonFactory,
                             final CredentialStore credentialStore,
                             final String clientId, final String clientSecret, final String authCode)
            throws Exception {
        final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientId, clientSecret, Arrays.asList(DriveScopes.DRIVE))
                .setAccessType("offline")
                .setApprovalPrompt("auto")
                .setCredentialStore(credentialStore).build();

        if (authCode == null) {
            credentialStore.delete(CLIENT_ID, null);

            final String url = flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URI).build();
            throw new MissingAuthorizationCodeException(url);
        }

        Credential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setClientSecrets(clientId, clientSecret)
                .build();
        if (!credentialStore.load(CLIENT_ID, credential)) {
            final GoogleTokenResponse response
                    = flow.newTokenRequest(authCode).setRedirectUri(REDIRECT_URI).execute();
            credential = flow.createAndStoreCredential(response, CLIENT_ID);
        }

        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("DriveService").build();
    }

    public File retrieveFileByTitle(final String title) throws IOException {
        return googleDriveUtils.retrieveFileByTitle(service, title);
    }

    public InputStream fetchFileByDownloadUrl(final String downloadUrl) throws IOException {
        return googleDriveUtils.fetchFileByDownloadUrl(service, downloadUrl);
    }

    public static final class MissingAuthorizationCodeException extends Exception {
        public final String url;

        public MissingAuthorizationCodeException(final String url) {
            this.url = url;
        }
    }
}


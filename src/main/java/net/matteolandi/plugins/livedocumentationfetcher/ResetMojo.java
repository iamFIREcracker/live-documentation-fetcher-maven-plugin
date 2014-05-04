package net.matteolandi.plugins.livedocumentationfetcher;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveBasedCredentialStore;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveService;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveUtils;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveAuthSettings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "reset", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ResetMojo extends AbstractMojo {
    @Parameter(required = true)
    public GoogleDriveAuthSettings googleDriveAuth;

    @Override
    public void execute() throws MojoExecutionException {
        final Log log = getLog();
        final NetHttpTransport httpTransport = new NetHttpTransport();
        final JacksonFactory jsonFactory = new JacksonFactory();
        final GoogleDriveUtils googleDriveUtils = new GoogleDriveUtils();

        try {
            final GoogleDriveBasedCredentialStore credentialStore =
                    new GoogleDriveBasedCredentialStore(
                            log, httpTransport, jsonFactory, googleDriveUtils,
                            googleDriveAuth.storageAccountEmail, googleDriveAuth.storagePrivateKeyPath);
            final GoogleDriveService googleDriveService =
                    new GoogleDriveService(
                            httpTransport, jsonFactory, credentialStore, googleDriveUtils,
                            googleDriveAuth.clientId, googleDriveAuth.clientSecret, googleDriveAuth.authCode);
            execute(googleDriveService);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void execute(final GoogleDriveService googleDriveService) throws Exception {
        googleDriveService.reset();
    }
}

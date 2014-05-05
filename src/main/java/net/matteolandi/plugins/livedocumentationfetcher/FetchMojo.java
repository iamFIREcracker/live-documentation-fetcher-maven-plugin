package net.matteolandi.plugins.livedocumentationfetcher;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.model.File;
import com.google.common.io.ByteStreams;
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

@Mojo(name = "fetch", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class FetchMojo extends AbstractMojo {
    private static final String MIME_TYPE = "text/html";
    private static final String EXTENSION = ".html";

    @Parameter(required = true)
    public GoogleDriveAuthSettings googleDriveAuth;

    @Parameter(defaultValue = "${project.build.directory}")
    public java.io.File outputDirectory;

    @Parameter(required = true)
    public Document[] documents;

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
        } catch (GoogleDriveService.MissingAuthorizationCodeException e) {
            log.error("Missing authorization code, get one at " + e.url);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void execute(final GoogleDriveService googleDriveService) throws Exception {
        for (final Document document : documents) {
            getLog().debug(String.format("Looking-up document with title: '%s'", document.title));

            final File file = googleDriveService.retrieveFileByTitle(document.title);
            if (file == null) {
                getLog().warn(String.format("Cannot find document with title: '%s'", document.title));
                continue;
            }

            final String downloadUrl = getDownloadUrl(file);
            if (downloadUrl == null) {
                getLog().warn(String.format("Cannot download document with title: '%s'", document.title));
                continue;
            }

            getLog().debug(String.format("Downloading: '%s'", downloadUrl));
            final InputStream inputStream = googleDriveService.fetchFileByDownloadUrl(downloadUrl);

            final java.io.File actualOutputDirectory = getActualOutputDirectory(document);
            final java.io.File destination = getDestinationFile(actualOutputDirectory, document.title, EXTENSION);
            getLog().info(String.format("Created: '%s'", destination.getAbsolutePath()));

            ByteStreams.copy(inputStream, new FileOutputStream(destination));
        }
    }

    private static String getDownloadUrl(final File file) {
        final Map<String, String> exportLinks = file.getExportLinks();
        if (exportLinks != null) {
            if (exportLinks.containsKey(MIME_TYPE)) {
                return exportLinks.get(MIME_TYPE);
            }
        }

        return file.getDownloadUrl();
    }

    private java.io.File getActualOutputDirectory(final Document document) {
        return (document.outputDirectory != null) ? document.outputDirectory : outputDirectory;
    }

    private static java.io.File getDestinationFile(final java.io.File outputDirectory, final String title,
                                                   final String extension) throws Exception {
        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdir()) {
                throw new Exception(
                        String.format("Cannot create destination folder: '%s'", outputDirectory.getAbsolutePath()));
            }
        }
        final String name = String.format("%s%s", title, extension);
        return new java.io.File(outputDirectory.getAbsolutePath(), name);
    }

    public static final class Document {
        @Parameter(required = true)
        public String title;

        @Parameter
        public java.io.File outputDirectory;

        @Override
        public String toString() {
            return "Document{" +
                    "title='" + title + '\'' +
                    ", outputDirectory='" + outputDirectory + '\'' +
                    '}';
        }
    }
}

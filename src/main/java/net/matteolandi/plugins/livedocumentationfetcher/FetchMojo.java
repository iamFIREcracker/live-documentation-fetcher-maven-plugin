package net.matteolandi.plugins.livedocumentationfetcher;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.model.File;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.io.ByteStreams;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveAuthSettings;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveBasedCredentialStore;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveService;
import net.matteolandi.plugins.livedocumentationfetcher.googledrive.GoogleDriveUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
            execute(googleDriveService, Executors.newCachedThreadPool());
        } catch (GoogleDriveService.MissingAuthorizationCodeException e) {
            final String message = "Missing authorization code, get one at " + e.url;
            log.error(message);
            throw new MojoExecutionException(message, e);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void execute(final GoogleDriveService googleDriveService,
                         final ExecutorService executorService) throws Exception {
        List<Future<?>> futures =
                FluentIterable
                .from(Arrays.asList(documents))
                .transform(new Function<Document, Future<?>>() {
                    @Nullable
                    @Override
                    public Future<?> apply(@Nullable final Document document) {
                        return executorService.submit(new Runnable() {
                            public void run() {
                                getLog().debug(String.format("Looking-up document with title: '%s'", document.title));

                                try {
                                    final File file = googleDriveService.retrieveFileByTitle(document.title);
                                    if (file == null) {
                                        throw new RuntimeException(
                                                String.format("Cannot find document with title: '%s'", document.title));
                                    }

                                    final String downloadUrl = getDownloadUrl(file);
                                    if (downloadUrl == null) {
                                        throw new RuntimeException(
                                                String.format(
                                                        "Cannot download document with title: '%s'", document.title));
                                    }

                                    getLog().debug(String.format("Downloading: '%s'", downloadUrl));
                                    final InputStream inputStream
                                            = googleDriveService.fetchFileByDownloadUrl(downloadUrl);

                                    final java.io.File actualOutputDirectory = getActualOutputDirectory(document);
                                    final java.io.File destination
                                            = getDestinationFile(actualOutputDirectory, document.title, EXTENSION);
                                    getLog().info(String.format("Created: '%s'", destination.getAbsolutePath()));

                                    ByteStreams.copy(inputStream, new FileOutputStream(destination));
                                } catch (Exception e) {
                                    throw new RuntimeException(e.getMessage(), e);
                                }
                            }
                        });
                    }})
                .toList();

        for (final Future<?> future : futures) {
            future.get();
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

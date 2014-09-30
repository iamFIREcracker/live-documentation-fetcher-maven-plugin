package net.matteolandi.plugins.livedocumentationfetcher;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.model.File;
import com.google.common.collect.Lists;
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
import rx.Notification;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Mojo(name = "fetch", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class FetchMojo extends AbstractMojo {
    private static final int MAX_RETRIES = 5;
    private static final int MAX_RETRY_DELAY_MILLIS = 5000;
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

        final Observable<GoogleDriveBasedCredentialStore> credentialStoreObservable =
                getGoogleDriveBasedCredentialStoreObservable(log, httpTransport, jsonFactory, googleDriveUtils);
        final Observable<GoogleDriveService> driveServiceObservable =
                getGoogleDriveServiceObservable(
                        log, httpTransport, jsonFactory, googleDriveUtils, credentialStoreObservable);
        final Observable<String> createdFilesObservable =
                getCreatedFilesObservable(log, driveServiceObservable, documents);

        createdFilesObservable.doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                log.error(throwable);
            }
        }).toBlocking().forEach(new Action1<String>() {
            @Override
            public void call(String s) {
                log.info(String.format("Created: '%s'", s));
            }
        });
    }

    private Observable<GoogleDriveBasedCredentialStore>
    getGoogleDriveBasedCredentialStoreObservable(final Log log, final NetHttpTransport httpTransport,
                                                 final JacksonFactory jsonFactory,
                                                 final GoogleDriveUtils googleDriveUtils) {
        return GoogleDriveBasedCredentialStore.observe(
                log, httpTransport, jsonFactory, googleDriveUtils,
                googleDriveAuth.storageAccountEmail, googleDriveAuth.storagePrivateKeyPath)
                .subscribeOn(Schedulers.io())
                .retryWhen(new RetryWithDelayWhenNetworkException(MAX_RETRIES, MAX_RETRY_DELAY_MILLIS));
    }

    private Observable<GoogleDriveService>
    getGoogleDriveServiceObservable(final Log log, final NetHttpTransport httpTransport,
                                    final JacksonFactory jsonFactory, final GoogleDriveUtils googleDriveUtils,
                                    final Observable<GoogleDriveBasedCredentialStore> credentialStoreObservable) {
        return credentialStoreObservable.flatMap(new Func1<GoogleDriveBasedCredentialStore, Observable<GoogleDriveService>>() {
            @Override
            public Observable<GoogleDriveService> call(final GoogleDriveBasedCredentialStore credentialStore) {
                return GoogleDriveService.observe(
                        log, httpTransport, jsonFactory, credentialStore, googleDriveUtils,
                        googleDriveAuth.clientId, googleDriveAuth.clientSecret, googleDriveAuth.authCode)
                        .subscribeOn(Schedulers.io())
                        .retryWhen(new RetryWithDelayWhenNetworkException(MAX_RETRIES, MAX_RETRY_DELAY_MILLIS));
            }
        });
    }

    private Observable<String>
    getCreatedFilesObservable(final Log log, final Observable<GoogleDriveService> googleDriveServiceObservable,
                              final Document[] documents) {
        return googleDriveServiceObservable.flatMap(new Func1<GoogleDriveService, Observable<String>>() {
            @Override
            public Observable<String> call(GoogleDriveService googleDriveService) {
                Collection<Observable<String>> accumulator = Lists.newArrayListWithExpectedSize(documents.length);

                for (final Document document : documents) {
                    final Observable<String> createdFileObservable =
                            downloadFile(log, googleDriveService, document)
                                    .retryWhen(new RetryWithDelayWhenNetworkException(
                                            MAX_RETRIES, MAX_RETRY_DELAY_MILLIS));

                    accumulator.add(createdFileObservable);
                }
                return Observable.merge(accumulator);
            }
        });
    }

    private Observable<String> downloadFile(final Log log, final GoogleDriveService googleDriveService,
                                            final Document document) {
        return Async.fromCallable(new Callable<String>() {
            @Override
            public String call() throws Exception {
                log.debug(String.format("Looking-up document with title: '%s'", document.title));

                final File file = googleDriveService.retrieveFileByTitle(document.title);
                if (file == null) {
                    warnAndThrow(log, "Cannot find document with title: '%s'", document.title);
                }

                final String downloadUrl = getDownloadUrl(file);
                if (downloadUrl == null) {
                    warnAndThrow(log, "Cannot export document with title: '%s'", document.title);
                }

                log.debug(String.format("Downloading: '%s'", downloadUrl));
                final InputStream inputStream
                        = googleDriveService.fetchFileByDownloadUrl(downloadUrl);

                final java.io.File actualOutputDirectory = getActualOutputDirectory(document);
                final java.io.File destination
                        = getDestinationFile(actualOutputDirectory, document.title, EXTENSION);

                ByteStreams.copy(inputStream, new FileOutputStream(destination));

                return destination.getAbsolutePath();
            }
        }, Schedulers.io());
    }

    private void warnAndThrow(final Log log, final String format, Object... args) throws RuntimeException {
        final String message = String.format(format, args);

        log.warn(message);
        throw new RuntimeException(message);
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

    /**
     * Adapted from this SO answer:  http://stackoverflow.com/a/25292833
     */
    public static class RetryWithDelayWhenNetworkException
            implements Func1<Observable<? extends Notification<?>>, Observable<?>> {
        private final int maxRetries;
        private final int maxRetryDelayMillis;
        private int retryCount;

        public RetryWithDelayWhenNetworkException(final int maxRetries, final int maxRetryDelayMillis) {
            this.maxRetries = maxRetries;
            this.maxRetryDelayMillis = maxRetryDelayMillis;
            this.retryCount = 0;
        }

        @Override
        public Observable<?> call(Observable<? extends Notification<?>> attempts) {
            return attempts
                    .flatMap(new Func1<Notification<?>, Observable<?>>() {
                        @Override
                        public Observable<?> call(Notification errorNotification) {
                            if (errorNotification.getThrowable() instanceof IOException) {
                                if (++retryCount < maxRetries) {
                                    // When this Observable calls onNext, the original
                                    // Observable will be retried (i.e. re-subscribed).

                                    return Observable.timer(
                                            (long) (Math.random() * maxRetryDelayMillis), TimeUnit.MILLISECONDS);
                                }
                            }

                            // Max retries hit. Just pass the error along.
                            return Observable.error(errorNotification.getThrowable());
                        }
                    });
        }
    }
}

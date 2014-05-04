package net.matteolandi.plugins.livedocumentationfetcher.googledrive;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.util.List;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.File;

public class GoogleDriveUtils {
    public File retrieveFileByTitle(final Drive service, final String title) throws IOException {
        final List<File> items = service.files().list()
                .setQ(String.format("title = '%s'", title))
                .setMaxResults(1)
                .execute().getItems();
        if (items.size() == 0) {
            return null;
        }
        return items.get(0);
    }

    public InputStream fetchByTitle(final Drive service, final String title) throws IOException {
        final File file = retrieveFileByTitle(service, title);
        if (file == null) {
            return null;
        }

        return fetchFileByDownloadUrl(service, file.getDownloadUrl());
    }

    public void uploadOrUpdateByTitle(final Drive service, final String title, final String mimeType,
                                      final String content) throws IOException {
        File file = retrieveFileByTitle(service, title);
        boolean isNew = false;
        if (file == null) {
            file = new File();
            isNew = true;
        }

        file.setTitle(title);
        file.setMimeType(mimeType);

        final InputStreamContent streamContent = new InputStreamContent(mimeType, new StringBufferInputStream(content));
        DriveRequest<File> request;

        if (isNew) {
            request = service.files().insert(file, streamContent);
        } else {
            request = service.files().update(file.getId(), file, streamContent);
        }
        request.getMediaHttpUploader().setDirectUploadEnabled(true);
        request.execute();
    }

    public void deleteFileByTitle(final Drive service, final String title) throws IOException {
        final File file = retrieveFileByTitle(service, title);
        if (file == null) {
            return;
        }

        service.files().delete(file.getId()).execute();
    }

    public InputStream fetchFileByDownloadUrl(final Drive service, final String downloadUrl) throws IOException {
        final HttpResponse resp =
                service.getRequestFactory().buildGetRequest(new GenericUrl(downloadUrl))
                        .execute();
        return resp.getContent();
    }

}

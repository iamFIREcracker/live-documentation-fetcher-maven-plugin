package net.matteolandi.plugins.livedocumentationfetcher.googledrive;

import org.apache.maven.plugins.annotations.Parameter;

public final class GoogleDriveAuthSettings {
    @Parameter(required = true)
    public String storageAccountEmail;

    @Parameter(required = true)
    public java.io.File storagePrivateKeyPath;

    @Parameter(required = true)
    public String clientId;

    @Parameter(required = true)
    public String clientSecret;

    @Parameter(required = true)
    public String authCode;

    @Override
    public String toString() {
        return "GoogleDriveAuthSettings{" +
                "storageAccountEmail='" + storageAccountEmail + '\'' +
                ", storagePrivateKeyPath=" + storagePrivateKeyPath +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", authCode='" + authCode + '\'' +
                '}';
    }
}

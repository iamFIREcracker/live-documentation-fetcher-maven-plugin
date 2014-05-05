# live-documentation-fetcher-maven-plugin

The aim of this Maven plugin is to allow its users to fetch from the *web* the
so called *live documentation*, that is the documentation and the set of
functional tests that should help developers at designing and validating new
features to add to their products.

Right now, the plugin can only be used to fetch documents from Google Drive (as
it seems like the best tool for doing collaborative editing), but nothing
prevent us from writing adapter for different web services.

## Setup

First of all, the plugin needs to communicate with a Google App, so please
head to the [Google Developers Console](https://console.developers.google.com),
and create a new one.

In the application dashboard, open the `APIs` menu (`APIs & auth > APIs`) and
enable the entries: *Drive API* and *Drive SDK*.

In the `Credentials` menu instead (`APIs & auth > Credentials`), create a new
*Service Account* client id that will be used by the plugin to store all the
OAuth2 tokens released so far by the application to the clients to allow for
a generic application (one that *Runs on a desktop computer or handheld
device*).

Still from the `Credentials` menu, create another client id this time for
a generic application (one which *Runs on a desktop computer or handheld
device*);  this instead will be used by the plugin to authenticate with the
application, and to fetch arguments.

## Configuration

Add the following to your build configuration:

    <project>
        ..
        <build>
            ..
            <plugins>
            ..
                <plugin>
                    <groupId>net.matteolandi</groupId>
                    <artifactId>live-documentation-fetcher-maven-plugin</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <configuration>
                        <googleDriveAuth>
                            <storageAccountEmail>${storageAccountEmail}</storageAccountEmail>
                            <storagePrivateKeyPath>${storagePrivateKeyPath}</storagePrivateKeyPath>
                            <clientId>${clientId}</clientId>
                            <clientSecret>${clientSecret}</clientSecret>
                            <authCode>${authCode}</authCode>
                        </googleDriveAuth>
                        <documents>
                            <document>
                                <title>Ideas</title>
                            </document>
                            <document>
                                <title>Questionario cinema</title>
                            </document>
                            <document>
                                <title>Live Documentation</title>
                            </document>
                        </documents>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>fetch</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </project>

Where:

- `storageAccountEmail` should be equal to the *Email Address* property of the
  service account previously created
- `storagePrivateKeyPath` should be a valid path pointing to the private key
  associated with the service account previously created
- `clientId` should be equal to the *Client ID* property of the client id for
  native application previously created
- `clientSecret` should be equal to the *Client Secret* property of the client
  id for native application previously created
- `authCode` should be the authorization token released by the application
  (later on will talk about how to get one)

By default, all the fetched documents will be placed inside
`${project.build.directory}`.


## Background

Those familiar with
[ATDD](http://en.wikipedia.org/wiki/Acceptance_test-driven_development) (and
[RobotFramework](http://robotframework.org/) in general) would already know the
difference between *tests* and *keywords*.  Tests should be part of this live
documentation, could include colors, images, and any other kind of media
convenient for future consultation, should be subject to *collaborative* editing
and should be accessible first and foremost by non-technical people.  Keywords
on the other hand, are just a means to translate tests into software inputs and
usually only developers have a real grasp of them.

That said, I see Google Drive as one possible *right* tool to create such live
documentation, and because of that I made this plugin to simplify the switch
from writing live documentation with non-collaborative tools such as
LibreOffice, to doing it the right way with 2014 tools!  After the switch you
won't need to check-in the live documentation anymore, that will indeed be
automatically downloaded at the time your tests are run.

## Credits

Special thanks to [fredpointzero](https://github.com/fredpointzero) for having
created, and shared
[google-drive-maven-plugin](https://github.com/fredpointzero/google-drive-maven-plugin).

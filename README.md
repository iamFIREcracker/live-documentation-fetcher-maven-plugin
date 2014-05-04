# live-documentation-fetcher-maven-plugin

The aim of this Maven plugin is to allow its users to fetch from the *web* the
so called *live documentation*, that is the documentation and the set of
functional tests that should help developers at designing and validating new
features to add to their products.

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

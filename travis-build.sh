#!/bin/bash
EXIT_STATUS=0

if [[ $TRAVIS_TAG =~ ^v[[:digit:]] ]]; then
	echo "Skipping tests to Publish release"
	./travis-publish.sh || EXIT_STATUS=$?
else
	./gradlew -Dgeb.env=chromeHeadless check --no-daemon -x gorm-hibernate5-spring-boot:test  || EXIT_STATUS=$?

    if [ $EXIT_STATUS -ne 0 ]; then
        echo "test failed => exit $EXIT_STATUS"
        exit $EXIT_STATUS
    fi

    ./gradlew gorm-hibernate5-spring-boot:test --no-daemon || EXIT_STATUS=$?

	if [ $EXIT_STATUS -ne 0 ]; then
        echo "gorm-hibernate5-spring-boot test failed => exit $EXIT_STATUS"
        exit $EXIT_STATUS
    fi

	./travis-publish.sh || EXIT_STATUS=$?
fi

exit $EXIT_STATUS

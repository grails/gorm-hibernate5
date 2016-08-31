#!/bin/bash
EXIT_STATUS=0

./gradlew --stop


if [[ $TRAVIS_TAG =~ ^v[[:digit:]] ]]; then
	echo "Skipping tests to Publish release"
	./travis-publish.sh || EXIT_STATUS=$?
else
	./gradlew check --refresh-dependencies -no-daemon -x gorm-hibernate5-spring-boot:test  || EXIT_STATUS=$?
	# if [[ $EXIT_STATUS -eq 0 ]]; then
	#     ./gradlew grails2-plugin:test -no-daemon || EXIT_STATUS=$?
	# fi
	if [[ $EXIT_STATUS -eq 0 ]]; then 
	    ./gradlew gorm-hibernate5-spring-boot:test --refresh-dependencies -no-daemon || EXIT_STATUS=$?
	fi

	./gradlew --stop

	if [[ $EXIT_STATUS -eq 0 ]]; then
	    ./travis-publish.sh || EXIT_STATUS=$?
	fi

fi

exit $EXIT_STATUS




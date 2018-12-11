#!/bin/bash

echo "Publishing..."

EXIT_STATUS=0

if [ "${TRAVIS_JDK_VERSION}" == "openjdk11" ] ; then
  exit $EXIT_STATUS
fi

echo "Publishing for branch $TRAVIS_BRANCH JDK: $TRAVIS_JDK_VERSION"


if [[ $TRAVIS_REPO_SLUG == "grails/gorm-hibernate5" && $TRAVIS_PULL_REQUEST == 'false' && $EXIT_STATUS -eq 0 ]]; then

  echo "Publishing archives"
  export GRADLE_OPTS="-Xmx1500m -Dfile.encoding=UTF-8"
  openssl aes-256-cbc -pass pass:$SIGNING_PASSPHRASE -in secring.gpg.enc -out secring.gpg -d
  
  gpg --keyserver keyserver.ubuntu.com --recv-key $SIGNING_KEY
  if [[ $TRAVIS_TAG =~ ^v[[:digit:]] ]]; then
    # for releases we upload to Bintray and Sonatype OSS
    echo "SIGNING KEY IS $SIGNING_KEY"
    ./gradlew -Psigning.keyId="$SIGNING_KEY" -Psigning.password="$SIGNING_PASSPHRASE" -Psigning.secretKeyRingFile="${TRAVIS_BUILD_DIR}/secring.gpg" uploadArchives --no-daemon -x grails-plugin:groovydoc || EXIT_STATUS=$?

    if [[ $EXIT_STATUS -eq 0 ]]; then
        ./gradlew -Psigning.keyId="$SIGNING_KEY" -Psigning.password="$SIGNING_PASSPHRASE" -Psigning.secretKeyRingFile="${TRAVIS_BUILD_DIR}/secring.gpg" publish --no-daemon -x grails-plugin:groovydoc|| EXIT_STATUS=$?
    fi


    if [[ $EXIT_STATUS -eq 0 ]]; then
        ./gradlew -Psigning.keyId="$SIGNING_KEY" -Psigning.password="$SIGNING_PASSPHRASE" -Psigning.secretKeyRingFile="${TRAVIS_BUILD_DIR}/secring.gpg" bintrayUpload --no-daemon -x grails-plugin:groovydoc || EXIT_STATUS=$?
    fi
  else
    echo "publishing snapshot"
    # for snapshots only to repo.grails.org
    ./gradlew -Psigning.keyId="$SIGNING_KEY" -Psigning.password="$SIGNING_PASSPHRASE" -Psigning.secretKeyRingFile="${TRAVIS_BUILD_DIR}/secring.gpg" publish -x grails-plugin:groovydoc || EXIT_STATUS=$?
  fi
  if [[ $EXIT_STATUS -eq 0 ]]; then
    echo "Publishing Successful."

    echo "Publishing Documentation..."
    ./gradlew docs:docs

    git config --global user.name "$GIT_NAME"
    git config --global user.email "$GIT_EMAIL"
    git config --global credential.helper "store --file=~/.git-credentials"
    echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials


    git clone https://${GH_TOKEN}@github.com/grails/grails-data-mapping.git -b gh-pages gh-pages --single-branch > /dev/null
    cd gh-pages

    if [[ -n $TRAVIS_TAG ]]; then
        version="$TRAVIS_TAG"
        version=${version:1}

        if [[ $TRAVIS_TAG =~ [M\d|RC\d] ]]; then            
           echo "Publishing Milestone Release"  
        else 
            mkdir -p latest/hibernate/
            cp -r ../docs/build/docs/. ./latest/hibernate/
            git add latest/hibernate/*
        fi     

        majorVersion=${version:0:4}
        majorVersion="${majorVersion}x"

        mkdir -p "$version/hibernate"
        cp -r ../docs/build/docs/. "./$version/hibernate/"
        git add "$version/hibernate/*"

        mkdir -p "$majorVersion/hibernate"
        cp -r ../docs/build/docs/. "./$majorVersion/hibernate/"
        git add "$majorVersion/hibernate/*"

    else
        # If this is the master branch then update the snapshot
        mkdir -p snapshot/hibernate/
        cp -r ../docs/build/docs/. ./snapshot/hibernate/

        git add snapshot/hibernate/*
    fi


    git commit -a -m "Updating Hibernate Docs for Travis build: https://travis-ci.org/$TRAVIS_REPO_SLUG/builds/$TRAVIS_BUILD_ID"
    git push origin HEAD
    cd ../../..
    rm -rf gh-pages
  fi  
  
fi

exit $EXIT_STATUS

#!/bin/bash

MODULE=model-scoring-$VERSION.$POST_TAG$BUILD_NUMBER


pushd target

    mkdir -p $MODULE/bin
    mkdir -p $MODULE/conf

    cp -Rv lib $MODULE/

    ls -la $MODULE

    cp model-scoring*.jar $MODULE/

    cp ../bin/model-scoring.sh $MODULE/bin/
    cp ../conf/application.conf.cf $MODULE/conf/application.conf
    cp ../manifest.yml.tpl $MODULE/manifest.yml


    zip  -r $MODULE.zip $MODULE

popd








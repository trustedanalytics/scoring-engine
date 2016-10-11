#!/bin/bash

MODULE=model-scoring-$VERSION.$POST_TAG$BUILD_NUMBER


pushd target

    mkdir -p $MODULE/bin

    cp -Rv lib $MODULE/

    ls -la $MODULE

    cp model-scoring*.jar $MODULE/

    cp ../bin/model-scoring.sh $MODULE/bin/


    zip  -r $MODULE.zip $MODULE

popd








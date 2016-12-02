#!/bin/bash
#
#  Copyright (c) 2016 Intel Corporation 
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

VERSION="${VERSION:-0.7.4}"
POST_TAG="${POST_TAG:-dev}"
BUILD_NUMBER="${BUILD_NUMBER:-1}"

MODULE=model-scoring-cf-$VERSION.$POST_TAG$BUILD_NUMBER


pushd target

    mkdir -p $MODULE/bin
    mkdir -p $MODULE/conf

    cp -Rv lib $MODULE/

    ls -la $MODULE

    cp model-scoring*.jar $MODULE/

    cp ../jq $MODULE/
    cp ../bin/cf.sh $MODULE/bin/
    cp ../conf/application.conf.cf $MODULE/conf/application.conf
    cp ../manifest.yml.tpl $MODULE/manifest.yml

    pushd $MODULE/
    zip  -r ../$MODULE.zip .
    popd

popd








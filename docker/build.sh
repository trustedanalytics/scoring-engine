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

MODEL_SCORING_PACKAGE=$(find `pwd`/../target -name "model-scoring-java-*.zip" )

echo MODEL_SCORING_PACKAGE $MODEL_SCORING_PACKAGE

unzip  $MODEL_SCORING_PACKAGE -d `pwd`

NAME="`basename $MODEL_SCORING_PACKAGE`"

echo Base name $NAME
NAME=${NAME//.zip/}
echo Base name $NAME

echo docker build --file=Dockerfile --tag=scoring-engine   \
 --build-arg HTTP_PROXY=$http_proxy \
 --build-arg HTTPS_PROXY=$http_proxy \
 --build-arg NO_PROXY=$no_proxy \
 --build-arg http_proxy=$http_proxy \
 --build-arg https_proxy=$http_proxy \
 --build-arg no_proxy=$no_proxy \
 --build-arg MODEL_SCORING_PACKAGE=$NAME \
 .
docker build --file=Dockerfile --tag=scoring-engine   \
 --build-arg HTTP_PROXY=$http_proxy \
 --build-arg HTTPS_PROXY=$http_proxy \
 --build-arg NO_PROXY=$no_proxy \
 --build-arg http_proxy=$http_proxy \
 --build-arg https_proxy=$http_proxy \
 --build-arg no_proxy=$no_proxy \
 --build-arg MODEL_SCORING_PACKAGE=$NAME \
 .


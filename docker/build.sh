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


MODEL_SCORING_PACKAGE=$(find `pwd`/../ -name "model-scoring-java-*.zip" -type f )

echo PATH `pwd`/../

echo $MODEL_SCORING_PACKAGE

if [ "$MODEL_SCORING_PACKAGE" == "" ]; then
    echo "couldn't find model scoring java package"
    exit 1
fi

echo MODEL_SCORING_PACKAGE $MODEL_SCORING_PACKAGE

unzip -o $MODEL_SCORING_PACKAGE -d `pwd`

NAME="`basename $MODEL_SCORING_PACKAGE`"

echo Base name $NAME
NAME=${NAME//.zip/}
echo Base name $NAME

echo docker build --file=Dockerfile --tag=scoring-engine-$BUILD_NUMBER   \
 --build-arg HTTP_PROXY=$http_proxy \
 --build-arg HTTPS_PROXY=$http_proxy \
 --build-arg NO_PROXY=$no_proxy \
 --build-arg http_proxy=$http_proxy \
 --build-arg https_proxy=$http_proxy \
 --build-arg no_proxy=$no_proxy \
 --build-arg MODEL_SCORING_PACKAGE=$NAME \
 .
docker build --file=Dockerfile --tag=scoring-engine-$BUILD_NUMBER   \
 --build-arg HTTP_PROXY=$http_proxy \
 --build-arg HTTPS_PROXY=$http_proxy \
 --build-arg NO_PROXY=$no_proxy \
 --build-arg http_proxy=$http_proxy \
 --build-arg https_proxy=$http_proxy \
 --build-arg no_proxy=$no_proxy \
 --build-arg MODEL_SCORING_PACKAGE=$NAME \
 .

mars=$(find `pwd`  -name "*.mar" -type f )
IFS=$'\n'
for mar in $mars
do
    echo
    echo $mar
    MARNAME="`basename $mar`"
    echo Base name $MARNAME
    MARNAME=${MARNAME//.mar/}
    echo Base name $MARNAME
    docker kill $MARNAME-$BUILD_NUMBER
    docker rm $MARNAME-$BUILD_NUMBER
done

for mar in $mars
do
    echo
    echo $mar
    MARNAME="`basename $mar`"
    echo Base name $MARNAME
    MARNAME=${MARNAME//.mar/}
    echo Base name $MARNAME
    DOCKERCONT=$MARNAME-$BUILD_NUMBER
    docker kill $DOCKERCONT
    docker rm $DOCKERCONT
    docker run -it -d -p 9100:9100 --name=$DOCKERCONT scoring-engine-5
    MAX=20
    code=$(curl -s -o /dev/null  localhost:9100 -w "%{http_code}")
    count=$((0))
    while [ $code -ne 200 ] || [ $count -gt $MAX ];
    do
        echo $code
        code=$(curl -s -o /dev/null  localhost:9100 -w "%{http_code}")
        count=$((0+1))
        sleep 1
    done
    code=$(curl -F "file=@$mar" -s -o /dev/null  localhost:9100/uploadMarFile -w "%{http_code}")
    if [ $code -ge 200 ] && [ $code -lt 400 ]; then
        echo yes $code
    else
        echo failed to load model $mar, http status code $code
        docker logs $DOCKERCONT
        exit 1
    fi
    docker kill $DOCKERCONT
    docker rm $DOCKERCONT
done
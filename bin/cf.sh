#!/bin/bash

echo "Starting ATK startup script"

set -o errexit
DIR="$( cd "$( dirname "$0" )" && pwd )"

export ATK_CONF_DIR="$DIR/../conf"
export YARN_CONF_DIR=$ATK_CONF_DIR

echo $DIR

$SCORING_JAR=$(find `pwd` -name "model-scoring*.jar" )

CP=$SCORING_JAR:$DIR/../lib/scala-library-2.10.5.jar:$DIR/../lib/config-1.2.1.jar:$DIR/../lib/scala-reflect-2.10.5.jar
CP=$DIR/../conf/logback.xml:$DIR/../conf:$CP
echo "Downloading jquery exectuable to parse environment variables"

export SEARCH_PATH="-Datk.module-loader.search-path=$DIR/../lib/"

jq=$DIR/../jq
echo "make jq executable"
chmod +x $jq


echo "Setting environment variables"
export APP_NAME=$(echo $VCAP_APPLICATION | $jq -r .application_name)
export APP_SPACE=$(echo $VCAP_APPLICATION | $jq -r .space_id)
export USE_HTTP=true

export FS_ROOT=$(echo $VCAP_SERVICES |  $jq -c -r '.hdfs[0].credentials.HADOOP_CONFIG_KEY["fs.defaultFS"]')

export FS_TECHNICAL_USER_NAME=$(echo $VCAP_SERVICES |  $jq -c -r '.hdfs[0].credentials.user')
export FS_TECHNICAL_USER_PASSWORD=$(echo $VCAP_SERVICES |  $jq -c -r '.hdfs[0].credentials.password')

env

pushd $ATK_CONF_DIR

configurationStart="<configuration>"
configurationEnd="</configuration>"
propertyStart="<property>"
propertyEnd="</property>"
nameStart="<name>"
nameEnd="</name>"
valueStart="<value>"
valueEnd="</value>"
tab="    "

hdfs_file="hdfs-site.xml"
hdfs_json="hdfs.json"

echo $VCAP_SERVICES |  $jq -c '.hdfs[0].credentials.HADOOP_CONFIG_KEY' > $hdfs_json

function buildSvcBrokerConfig {
(echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
 echo $configurationStart
) >> $1
key_count=$(cat $2 | $jq -c 'keys' | $jq 'length' )
keys=$(cat $2 | $jq -c 'keys')
count=0
while [ $count -lt $key_count ]
do
	key=$( echo $keys | $jq -c -r ".[$count]")
	value=$( cat $2 | $jq -c  ".[\"$key\"]" | sed -e "s|\"||g" )
	count=$((count+1))
	echo $tab$propertyStart$nameStart${key}$nameEnd$valueStart${value}$valueEnd$propertyEnd >> $1
done
echo $configurationEnd >> $1
rm $2
}

buildSvcBrokerConfig $hdfs_file $hdfs_json

popd

pushd $DIR/..
pwd
export PWD=`pwd`

export PATH=$PWD/.java-buildpack/open_jdk_jre/bin:$PATH
export JAVA_HOME=$PWD/.java-buildpack/open_jdk_jre


# Create temporary directory for extracting the model, and add it to the library path
# It is difficult to modify the library path for dynamic libraries after the Java process has started
# LD_LIBRARY_PATH allows the OS to find the dynamic libraries and any dependencies
export MODEL_TMP_DIR=`mktemp -d -t tap-scoring-modelXXXXXXXXXXXXXXXXXX`
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$MODEL_TMP_DIR
export MODEL_SCORING_MAIN="org.trustedanalytics.scoring.MyMainFunction"

echo java $@ -XX:MaxPermSize=384m $SEARCH_PATH -cp "$CP" -Datk.scoring-engine.tmpdir="$MODEL_TMP_DIR" $MODEL_SCORING_MAIN
java $@ -XX:MaxPermSize=384m $SEARCH_PATH -cp "$CP" -Datk.scoring-engine.tmpdir="$MODEL_TMP_DIR" $MODEL_SCORING_MAIN

popd

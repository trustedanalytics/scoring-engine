---
applications:
- name: scoring-engine-spark-tk
  command: bin/cf.sh
  buildpack: tap-java-buildpack
  memory: 1G
  disk_quota: 1G
  timeout: 180
  instances: 1
  services:
    - hdfs-for-se
    - kerberos-for-atk
  env:
    VERSION: "20161014"

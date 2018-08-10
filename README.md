## Automated setup of a Hadoop cluster (HDFS + YARN) in pseudo distributed mode and Hive Metastore

### Pre-requisite
Make sure password-less ssh is setup correctly on the local machine

### Directory layout
- setup/binaries : This is where Hadoop and Hive binaries are downloaded and un tarred to folders hadoop and hive
- setup/conf contains HDFS, YARN and Metastore configuration. These configuration settings are copied over to folders hadoop and hive
respectively under binaries during setup.

### setup.cfg
Update setup/conf/setup.cfg as required. For first time run, 'download.binaries' should be true and then mark it false for subsequent runs so that hadoop and
hive tgzs are not downloaded on every run.

### Setting up cluster
```./gradlw setup:clusterSetup```

This will setup HDFS, YARN and Metastore. Application logging will be under setup/build/logs . Hadoop specific logs will be under
setup/binaries/hadoop/logs.  Under each platform, environment variables needed to talk to the platform correctly will be written
E.g envs vars to talk to HDFS, YARN will be written under setup/binaries/hadoop/env_vars.sh, and for Hive they will be under setup/binaries/hive/env_vars.sh

### Verification
Once the cluster is up. You can play around with HDFS, check whether YARN is up etc.

```
cd setup/binaries/hadoop
. env_vars.sh  # Set env variables
bin/hdfs dfs -ls /

cd setup/binaries/hive
. env_var.sh # Set env variables
bin/hive  # Start Hive

Verify RM is up: http://localhost:8088/cluster
```

#!/usr/bin/env bash

bin/hive --service metastore -p 60083 &
echo $! > /tmp/metastore.pid
echo "Started Metastore"
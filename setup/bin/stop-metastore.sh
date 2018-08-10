#!/usr/bin/env bash

kill -9 `cat /tmp/metastore.pid`
echo "Stopped Metastore"

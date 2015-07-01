#!/bin/bash
# MessageReceiver
#
# Java background service for receiving messages and say them

pid=`ps aux | grep -i "java.*MessageReceiver.*\.jar" | grep -v "grep" |  awk '{print $2}'`
echo "Stopping" $pid
kill -9 $pid

#!/bin/bash
# MessageReceiver
#
# Java background service for receiving messages and say them

dir=`expr match "$0" '\(.*/\)'`
nohup java -jar $dir"/MessageReceiverPacked.jar" daemon &

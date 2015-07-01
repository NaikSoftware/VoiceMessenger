#!/bin/bash
# MessageReceiver
#
# Java background service for receiving messages and say them

nohup java -jar "MessageReceiverPacked.jar" daemon &

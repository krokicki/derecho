#!/bin/sh
SCRIPT=$(readlink -f $0)
SCRIPTPATH=$(dirname $SCRIPT)
cd "$SCRIPTPATH"
java -Xms512M -Xmx4096M -DCONFIG=app.properties -jar derecho.jar
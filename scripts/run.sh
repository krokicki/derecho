#!/bin/sh
SCRIPT=$(readlink -f $0)
SCRIPTPATH=$(dirname $SCRIPT)
cd "$SCRIPTPATH"
java -da -Xms512M -Xmx4096M -DAPP_CONFIG=app.properties -DGRID_CONFIG=grid_config.xml -DAPP_PACKAGE=$SCRIPTPATH -jar derecho.jar

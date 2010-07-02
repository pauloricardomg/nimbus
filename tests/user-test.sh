#!/bin/bash

NIMBUS_HOME_REL="`dirname $0`/.."
NIMBUS_HOME=`cd $NIMBUS_HOME_REL; pwd`
export NIMBUS_HOME

PYTHON_EXE="/usr/bin/env python -Wignore::DeprecationWarning"

NIMBUS_WEBDIR="$NIMBUS_HOME/web"

NIMBUS_PYLIB="$NIMBUS_WEBDIR/lib/python"
NIMBUS_PYSRC="$NIMBUS_WEBDIR/src/python"

source $NIMBUS_HOME/cumulus/env.sh
PYTHONPATH="${PYTHONPATH}:$NIMBUS_PYSRC:$NIMBUS_PYLIB:$PYTHONPATH:$NIMBUS_HOME/sbin:${PYTHONPATH}"
export PYTHONPATH

DJANGO_SETTINGS_MODULE="nimbusweb.portal.settings"
export DJANGO_SETTINGS_MODULE

cd $NIMBUS_HOME/
./bin/nimbusctl restart

cd $NIMBUS_HOME/libexec
nosetests ../tests/user_tests.py ../tests/ec2_test.py

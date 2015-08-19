#!/bin/bash

confd_cli -J --user=admin --groups=admin<<EOF
config private
load merge initial.cfg
commit
exit
EOF

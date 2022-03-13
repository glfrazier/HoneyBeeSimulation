#!/usr/bin/env bash

NORMALIZER=$1
divisor=1
dname=$(dirname $2)
case $NORMALIZER in
    N) divisor=1 ;;
    D) divisor=$(head -n 1 ${dname}/domesticLiveHives.csv | awk '{ print $2; }') ;;
    F) divisor=$(head -n 1 ${dname}/feralLiveHives.csv | awk '{ print $2; }') ;;
esac

perl -n -e "@x = split(/ /); \$a=\$x[0];\$b=\$x[1]/$divisor;print \"\$a  \$b\\n\";" $2

